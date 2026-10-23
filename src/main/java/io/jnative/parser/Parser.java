/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.parser;

import io.jnative.ast.*;
import io.jnative.diagnostics.DiagnosticReporter;
import io.jnative.diagnostics.SourceLocation;
import io.jnative.lexer.Token;
import io.jnative.lexer.TokenType;
import io.jnative.semantic.Visibility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Recursive-descent parser for the JNative Java subset (0.8 native data model).
 * @author 张晓达
 */
public final class Parser {
    private final List<Token> tokens;
    private final DiagnosticReporter reporter;
    private int current;
    private final Set<String> knownClassNames = new HashSet<>();
    /** Simple names of the types enclosing the declaration being parsed (outermost first). */
    private final java.util.ArrayDeque<String> typeNesting = new java.util.ArrayDeque<>();
    /** Nested types discovered while parsing a body; flattened into the compilation unit. */
    private final List<ClassDeclaration> nestedTypes = new ArrayList<>();
    /** Dotted full name ({@code Outer.Inner}) of the type whose members are being parsed. */
    private String currentTypeFullName = "";
    /** Types declared in other compilation units (stdlib, deps, siblings). */
    private final Set<String> programTypeNames;
    private final Map<String, List<MarkerAnnotation>> typeAnnotations = new LinkedHashMap<>();
    private final Map<String, List<MarkerAnnotation>> methodAnnotations = new LinkedHashMap<>();
    private final Map<String, List<MarkerAnnotation>> fieldAnnotations = new LinkedHashMap<>();
    /** Active language package (not package-manager coordinates). */
    private String currentPackage = "";
    private final List<ImportDeclaration> currentImports = new ArrayList<>();
    private boolean packageDeclaredInScope;

    /** A local variable visible at a point of the body being parsed (type is null when not syntactically known). */
    private record LocalInfo(TypeName type, boolean isArray) {
    }

    /** Lexical scopes of locals/parameters (innermost first); used to find what an anonymous class captures. */
    private final java.util.ArrayDeque<Map<String, LocalInfo>> localScopes = new java.util.ArrayDeque<>();
    private int anonymousCounter;
    /** Built-in closure classes that user classes cannot extend (see SemanticAnalyzer.installSam). */
    private static final Set<String> BUILTIN_SAM_CLASSES = Set.of(
            "Runnable", "IntUnaryFunction", "IntFunction", "IntBinaryFunction", "IntCompare", "HttpHandler");

    private void pushLocalScope() {
        localScopes.push(new HashMap<>());
    }

    private void popLocalScope() {
        localScopes.pop();
    }

    private void declareLocal(String name, TypeName type, boolean isArray) {
        if (!localScopes.isEmpty()) {
            localScopes.peek().put(name, new LocalInfo(type, isArray));
        }
    }

    /** {@code var x = init;}: only {@code new T(..)} and simple literals have a syntactically known type. */
    private TypeName inferLocalType(Expression init) {
        if (init instanceof NewObjectExpression n && !n.diamond()) {
            return TypeName.ofClass(n.className(), n.typeArguments(), n.location());
        }
        if (init instanceof LiteralExpression l) {
            return switch (l.kind()) {
                case INT -> TypeName.ofInt(l.location());
                case LONG -> TypeName.ofLong(l.location());
                case FLOAT -> TypeName.ofFloat(l.location());
                case DOUBLE -> TypeName.ofDouble(l.location());
                case BOOLEAN -> TypeName.ofBoolean(l.location());
                case CHAR -> TypeName.ofChar(l.location());
                case STRING -> TypeName.ofClass("String", l.location());
            };
        }
        return null;
    }

    public Parser(List<Token> tokens, DiagnosticReporter reporter) {
        this(tokens, reporter, Set.of());
    }

    public Parser(List<Token> tokens, DiagnosticReporter reporter, Set<String> programTypeNames) {
        this.tokens = tokens;
        this.reporter = reporter;
        this.programTypeNames = programTypeNames == null ? Set.of() : Set.copyOf(programTypeNames);
    }

    public CompilationUnit parse(String fileName) {
        // Pre-scan class names so local variable declarations like `Person p` work.
        collectClassNames();
        current = 0;
        typeAnnotations.clear();
        methodAnnotations.clear();
        fieldAnnotations.clear();
        currentPackage = "";
        currentImports.clear();
        packageDeclaredInScope = false;

        List<ClassDeclaration> classes = new ArrayList<>();
        var loc = peek().location();
        try {
            while (!isAtEnd()) {
                if (check(TokenType.PACKAGE)) {
                    parsePackageDeclaration();
                    continue;
                }
                if (check(TokenType.IMPORT)) {
                    parseImportDeclaration();
                    continue;
                }
                classes.add(parseTypeDeclaration());
                classes.addAll(nestedTypes);
                nestedTypes.clear();
            }
        } catch (ParseException e) {
            reporter.error(e.location(), e.getMessage());
            synchronize();
        }
        return new CompilationUnit(
                fileName,
                classes,
                Map.copyOf(typeAnnotations),
                Map.copyOf(methodAnnotations),
                Map.copyOf(fieldAnnotations),
                loc);
    }

    private void parsePackageDeclaration() {
        Token pkgTok = consume(TokenType.PACKAGE, "expected 'package'");
        if (packageDeclaredInScope && !currentPackage.isEmpty()) {
            // Mid-compilation-unit package switch (concatenated multi-file): reset imports.
        }
        String name = parseQualifiedName("expected package name");
        consume(TokenType.SEMICOLON, "expected ';' after package declaration");
        currentPackage = name;
        currentImports.clear();
        packageDeclaredInScope = true;
        if (name.isEmpty()) {
            reporter.error(pkgTok.location(), "invalid package name");
        }
    }

    private void parseImportDeclaration() {
        Token imp = consume(TokenType.IMPORT, "expected 'import'");
        if (match(TokenType.STATIC)) {
            reporter.error(imp.location(),
                    "static import is not supported; import the type and reference members explicitly");
            skipUntilSemicolon();
            return;
        }
        String name = parseQualifiedName("expected type name after import");
        if (match(TokenType.DOT)) {
            if (match(TokenType.STAR)) {
                // Stdlib / flattened packages: `import jnative.ui.*` is accepted and
                // resolves against simple type names already known in the CU (UiLib).
                // Other wildcards remain unsupported.
                consume(TokenType.SEMICOLON, "expected ';' after import");
                if (name.startsWith("jnative.")) {
                    currentImports.add(new ImportDeclaration(name + ".*", imp.location()));
                } else if (isBuiltinJavaPackage(name)) {
                    // java.util.* / java.util.function.*: the supported subset is built in.
                } else {
                    reporter.error(imp.location(),
                            "wildcard import is not supported; use an explicit type import");
                }
                return;
            }
            reporter.error(peek().location(), "expected ';' after import");
            skipUntilSemicolon();
            return;
        }
        consume(TokenType.SEMICOLON, "expected ';' after import");
        if (!name.contains(".")) {
            reporter.error(imp.location(),
                    "import must be a fully qualified type name (e.g. com.acme.Foo)");
            return;
        }
        if (isBuiltinJavaPackage(name.substring(0, name.lastIndexOf('.')))) {
            // `import java.util.ArrayList;` — the type is built in; an unsupported type fails
            // later as an unknown type.
            return;
        }
        currentImports.add(new ImportDeclaration(name, imp.location()));
        knownClassNames.add(name.substring(name.lastIndexOf('.') + 1));
    }

    /** Packages whose supported subset is provided by the built-in stdlib. */
    /** Maps LinkedHashMap/LinkedHashSet to their ordered HashMap/HashSet backing, unless user-declared. */
    private String linkedHashAlias(String name) {
        if (programTypeNames.contains(name)) {
            return null;
        }
        return switch (name) {
            case "LinkedHashMap" -> "HashMap";
            case "LinkedHashSet" -> "HashSet";
            case "LinkedList" -> "ArrayDeque";
            case "Stack" -> "ArrayList";
            default -> null;
        };
    }

    private static boolean isBuiltinJavaPackage(String pkg) {
        return "java.util".equals(pkg) || "java.util.function".equals(pkg);
    }

    private String parseQualifiedName(String message) {
        Token first = consume(TokenType.IDENTIFIER, message);
        StringBuilder sb = new StringBuilder(first.lexeme());
        // Stop before `.*` so import wildcards can be diagnosed separately.
        while (check(TokenType.DOT)
                && peekAt(1) != null
                && peekAt(1).type() == TokenType.IDENTIFIER) {
            advance(); // DOT
            Token next = consume(TokenType.IDENTIFIER, "expected identifier after '.'");
            sb.append('.').append(next.lexeme());
        }
        return sb.toString();
    }

    private void skipUntilSemicolon() {
        while (!isAtEnd() && !check(TokenType.SEMICOLON) && !check(TokenType.EOF)) {
            advance();
        }
        if (check(TokenType.SEMICOLON)) {
            advance();
        }
    }

    private void collectClassNames() {
        knownClassNames.clear();
        knownClassNames.add("String");
        knownClassNames.add("var"); // local variable type inference
        knownClassNames.add("Object");
        knownClassNames.add("Throwable");
        knownClassNames.add("Exception");
        knownClassNames.add("RuntimeException");
        knownClassNames.add("NullPointerException");
        knownClassNames.add("ArrayIndexOutOfBoundsException");
        knownClassNames.add("NegativeArraySizeException");
        knownClassNames.add("ClassCastException");
        knownClassNames.add("ArithmeticException");
        knownClassNames.add("IOException");
        knownClassNames.add("FileNotFoundException");
        knownClassNames.add("SocketException");
        knownClassNames.add("ConnectException");
        knownClassNames.add("UnknownHostException");
        knownClassNames.add("SocketTimeoutException");
        knownClassNames.add("IllegalArgumentException");
        knownClassNames.add("IllegalStateException");
        knownClassNames.add("IllegalMonitorStateException");
        knownClassNames.add("IndexOutOfBoundsException");
        knownClassNames.add("NoSuchElementException");
        knownClassNames.add("ConcurrentModificationException");
        knownClassNames.add("NativePtr");
        knownClassNames.add("NativeMemory");
        knownClassNames.add("Native");
        knownClassNames.add("File");
        knownClassNames.add("TcpSocket");
        knownClassNames.add("TcpServer");
        knownClassNames.add("Bytes");
        knownClassNames.add("Thread");
        knownClassNames.add("Mutex");
        knownClassNames.add("Condition");
        knownClassNames.add("AtomicInt");
        knownClassNames.add("AtomicLong");
        knownClassNames.add("AtomicBool");
        knownClassNames.add("Runnable");
        knownClassNames.add("IntUnaryFunction");
        knownClassNames.add("IntBinaryFunction");
        knownClassNames.add("IntFunction");
        knownClassNames.add("IntCompare");
        knownClassNames.add("HttpHandler");
        knownClassNames.add("NativeCallbackHandle");
        knownClassNames.add("Future");
        knownClassNames.addAll(programTypeNames);
        // Aliased to the insertion-ordered HashMap/HashSet (see linkedHashAlias).
        knownClassNames.add("LinkedHashMap");
        knownClassNames.add("LinkedHashSet");
        knownClassNames.add("LinkedList");
        knownClassNames.add("Stack");
        int saved = current;
        current = 0;
        while (!isAtEnd()) {
            if (check(TokenType.AT)) {
                skipAnnotation();
                continue;
            }
            if (match(TokenType.PUBLIC)) {
                // continue
            }
            match(TokenType.ABSTRACT);
            if ((match(TokenType.CLASS) || match(TokenType.INTERFACE) || match(TokenType.ENUM))
                    && check(TokenType.IDENTIFIER)) {
                knownClassNames.add(advance().lexeme());
                skipTypeParameterTokens();
            } else if (!isAtEnd()) {
                advance();
            }
        }
        current = saved;
    }

    /** Best-effort skip of {@code @Name(...)} during class-name pre-scan. */
    private void skipAnnotation() {
        if (!match(TokenType.AT)) {
            return;
        }
        if (check(TokenType.IDENTIFIER)) {
            advance();
        }
        if (match(TokenType.LPAREN)) {
            int depth = 1;
            while (!isAtEnd() && depth > 0) {
                if (check(TokenType.LPAREN)) {
                    depth++;
                } else if (check(TokenType.RPAREN)) {
                    depth--;
                }
                advance();
            }
        }
    }

    private ClassDeclaration parseTypeDeclaration() {
        boolean nativeStruct = false;
        EffectContract typeEffects = EffectContract.empty(peek().location());
        List<MarkerAnnotation> pendingTypeAnns = new ArrayList<>();
        while (check(TokenType.AT)) {
            ParsedAnnotation ann = parseAnnotation();
            if ("NativeStruct".equals(ann.name())) {
                nativeStruct = true;
            } else if ("Native".equals(ann.name())) {
                reporter.error(ann.location(), "class-level @Native is not supported");
            } else if (ann.effectContract() != null) {
                typeEffects = typeEffects.merge(ann.effectContract());
            } else if (ann.marker() != null) {
                pendingTypeAnns.add(ann.marker());
            } else if (isIgnoredAnnotation(ann)) {
                // accepted for source compatibility
            } else {
                reporter.error(ann.location(),
                        "@" + ann.name() + " is not allowed on type declarations");
            }
        }
        Visibility typeVis = parseVisibilityModifier();
        boolean isAbstract = false;
        boolean isFinal = false;
        boolean nestedStatic = false;
        for (int i = 0; i < 4; i++) {
            if (!isAbstract && match(TokenType.ABSTRACT)) {
                isAbstract = true;
            } else if (!isFinal && match(TokenType.FINAL)) {
                isFinal = true;
            } else if (!nestedStatic && !typeNesting.isEmpty() && match(TokenType.STATIC)) {
                // nested types are always flattened (no enclosing instance), so 'static' is implied
                nestedStatic = true;
            } else {
                break;
            }
        }
        if (isAbstract && isFinal) {
            reporter.error(previous().location(), "illegal combination of modifiers: abstract and final");
        }
        boolean isInterface = false;
        boolean isEnum = false;
        if (match(TokenType.INTERFACE)) {
            isInterface = true;
            if (nativeStruct) {
                reporter.error(previous().location(), "@NativeStruct cannot be an interface");
            }
            if (isFinal) {
                reporter.error(previous().location(), "interface cannot be final");
                isFinal = false;
            }
        } else if (match(TokenType.ENUM)) {
            isEnum = true;
            if (nativeStruct) {
                reporter.error(previous().location(), "@NativeStruct cannot be an enum");
            }
            if (isAbstract) {
                reporter.error(previous().location(), "enum cannot be abstract");
            }
            if (isFinal) {
                reporter.error(previous().location(), "enum is implicitly final; remove 'final'");
                isFinal = false;
            }
        } else {
            consume(TokenType.CLASS, "expected 'class', 'interface', or 'enum'");
        }
        Token name = consume(TokenType.IDENTIFIER, "expected type name");
        String fullName = typeNesting.isEmpty()
                ? name.lexeme() : String.join(".", typeNesting) + "." + name.lexeme();
        if (typeNesting.isEmpty() && (typeVis == Visibility.PRIVATE || typeVis == Visibility.PROTECTED)) {
            reporter.error(name.location(),
                    "top-level type cannot be " + typeVis.name().toLowerCase()
                            + "; use public or package-private");
            typeVis = Visibility.PACKAGE;
        }
        if (!pendingTypeAnns.isEmpty()) {
            typeAnnotations.put(fullName, List.copyOf(pendingTypeAnns));
        }
        knownClassNames.add(name.lexeme());
        String pkg = currentPackage == null ? "" : currentPackage;
        List<ImportDeclaration> importsSnapshot = List.copyOf(currentImports);

        if (isEnum) {
            return parseEnumBody(name, fullName, pkg, importsSnapshot, typeVis);
        }

        List<TypeParameterDeclaration> typeParams = parseTypeParameterList();
        // Locals/params may use class type parameters as types (e.g. T value = ...).
        for (TypeParameterDeclaration tp : typeParams) {
            knownClassNames.add(tp.name());
        }
        String superClassName = null;
        List<TypeName> superClassTypeArgs = List.of();
        List<TypeName> implementedInterfaces = new ArrayList<>();
        if (isInterface) {
            if (match(TokenType.EXTENDS)) {
                implementedInterfaces.addAll(parseTypeNameList("expected interface name"));
            }
            if (match(TokenType.IMPLEMENTS)) {
                reporter.error(previous().location(), "interfaces use 'extends', not 'implements'");
            }
        } else {
            if (match(TokenType.EXTENDS)) {
                TypeName superType = parseClassTypeRef("expected superclass name");
                superClassName = superType.name();
                superClassTypeArgs = superType.hasTypeArguments()
                        ? superType.typeArguments() : List.of();
                if (nativeStruct) {
                    reporter.error(superType.location(),
                            "@NativeStruct types cannot extend a class");
                }
            }
            if (match(TokenType.IMPLEMENTS)) {
                if (nativeStruct) {
                    reporter.error(previous().location(),
                            "@NativeStruct types cannot implement interfaces");
                }
                implementedInterfaces.addAll(parseTypeNameList("expected interface name"));
            }
        }
        consume(TokenType.LBRACE, "expected '{'");

        List<FieldDeclaration> fields = new ArrayList<>();
        List<MethodDeclaration> methods = new ArrayList<>();
        String savedFull = currentTypeFullName;
        currentTypeFullName = fullName;
        typeNesting.addLast(name.lexeme());
        try {
            while (!check(TokenType.RBRACE) && !isAtEnd()) {
                parseClassMember(name.lexeme(), fields, methods, nativeStruct, isInterface, isAbstract,
                        typeEffects);
            }
        } finally {
            typeNesting.removeLast();
            currentTypeFullName = savedFull;
        }
        consume(TokenType.RBRACE, "expected '}'");
        return new ClassDeclaration(
                fullName, pkg, importsSnapshot, typeVis, typeParams, superClassName, superClassTypeArgs,
                implementedInterfaces, fields, methods,
                nativeStruct, isInterface, isAbstract, isFinal, name.location());
    }

    /**
     * Enum declaration: {@code enum Name [implements I, J] { A, B(1, "x"); fields; Name(..) {..}; methods }}.
     * Each constant becomes a {@code public static} field of the enum type initialised with
     * {@code new Name(args)}; the semantic and IR phases then fill in the name and ordinal.
     * Constant-specific class bodies are not supported.
     */
    private ClassDeclaration parseEnumBody(
            Token name, String fullName, String pkg, List<ImportDeclaration> imports, Visibility typeVis) {
        if (match(TokenType.LT)) {
            throw error(previous(), "enum type parameters are not supported");
        }
        if (match(TokenType.EXTENDS)) {
            throw error(previous(), "an enum cannot extend a class");
        }
        List<TypeName> implementedInterfaces = new ArrayList<>();
        if (match(TokenType.IMPLEMENTS)) {
            implementedInterfaces.addAll(parseTypeNameList("expected interface name"));
        }
        consume(TokenType.LBRACE, "expected '{' after enum name");
        List<String> constants = new ArrayList<>();
        List<FieldDeclaration> fields = new ArrayList<>();
        List<MethodDeclaration> methods = new ArrayList<>();
        String savedFull = currentTypeFullName;
        currentTypeFullName = fullName;
        typeNesting.addLast(name.lexeme());
        try {
            if (!check(TokenType.RBRACE)) {
                do {
                    if (check(TokenType.SEMICOLON) || check(TokenType.RBRACE)) {
                        break;
                    }
                    Token c = consume(TokenType.IDENTIFIER, "expected enum constant name");
                    List<Expression> args = List.of();
                    if (match(TokenType.LPAREN)) {
                        args = parseArgumentList();
                        consume(TokenType.RPAREN, "expected ')' after enum constant arguments");
                    }
                    if (match(TokenType.LBRACE)) {
                        throw error(previous(),
                                "enum constant class bodies are not supported in this language profile");
                    }
                    constants.add(c.lexeme());
                    fields.add(new FieldDeclaration(
                            TypeName.ofClass(fullName, c.location()), false, c.lexeme(),
                            Visibility.PUBLIC, true, false,
                            new NewObjectExpression(fullName, args, c.location()), c.location()));
                } while (match(TokenType.COMMA));
            }
            if (match(TokenType.SEMICOLON)) {
                while (!check(TokenType.RBRACE) && !isAtEnd()) {
                    parseClassMember(name.lexeme(), fields, methods, false, false, false,
                            EffectContract.empty(name.location()));
                }
            }
        } finally {
            typeNesting.removeLast();
            currentTypeFullName = savedFull;
        }
        consume(TokenType.RBRACE, "expected '}' after enum body");
        if (constants.isEmpty()) {
            reporter.error(name.location(), "enum must declare at least one constant");
        }
        return new ClassDeclaration(
                fullName, pkg, imports, typeVis, List.of(), null, List.of(), implementedInterfaces,
                fields, methods, false, false, false, true, true,
                List.copyOf(constants), name.location());
    }

    private Visibility parseVisibilityModifier() {
        boolean isPublic = match(TokenType.PUBLIC);
        boolean isProtected = match(TokenType.PROTECTED);
        boolean isPrivate = match(TokenType.PRIVATE);
        // Allow any order / duplicates lightly
        if (!isPublic) {
            isPublic = match(TokenType.PUBLIC);
        }
        if (!isProtected) {
            isProtected = match(TokenType.PROTECTED);
        }
        if (!isPrivate) {
            isPrivate = match(TokenType.PRIVATE);
        }
        Visibility v = Visibility.fromModifiers(isPublic, isProtected, isPrivate);
        if (v == null) {
            reporter.error(previous().location(), "conflicting access modifiers");
            return Visibility.PACKAGE;
        }
        return v;
    }

    private List<TypeParameterDeclaration> parseTypeParameterList() {
        if (!match(TokenType.LT)) {
            return List.of();
        }
        List<TypeParameterDeclaration> params = new ArrayList<>();
        do {
            Token paramName = consume(TokenType.IDENTIFIER, "expected type parameter name");
            List<TypeName> bounds = new ArrayList<>();
            if (match(TokenType.EXTENDS)) {
                bounds.add(parseClassTypeRef("expected type bound"));
            }
            params.add(new TypeParameterDeclaration(
                    paramName.lexeme(), bounds, paramName.location()));
        } while (match(TokenType.COMMA));
        consume(TokenType.GT, "expected '>' after type parameters");
        return params;
    }

    private List<TypeName> parseTypeNameList(String message) {
        List<TypeName> types = new ArrayList<>();
        do {
            types.add(parseClassTypeRef(message));
        } while (match(TokenType.COMMA));
        return types;
    }

    /** Class or interface reference with optional type arguments (allows {@code a.b.C}). */
    private TypeName parseClassTypeRef(String message) {
        Token id = consume(TokenType.IDENTIFIER, message);
        StringBuilder qn = new StringBuilder(id.lexeme());
        while (match(TokenType.DOT)) {
            Token next = consume(TokenType.IDENTIFIER, "expected identifier after '.'");
            qn.append('.').append(next.lexeme());
        }
        List<TypeName> args = List.of();
        if (match(TokenType.LT)) {
            if (!check(TokenType.GT)) {
                args = parseTypeArgumentList();
            }
            consume(TokenType.GT, "expected '>' after type arguments");
        }
        return TypeName.ofClass(qn.toString(), args, id.location());
    }

    private void parseClassMember(String className,
                                  List<FieldDeclaration> fields,
                                  List<MethodDeclaration> methods,
                                  boolean nativeStruct,
                                  boolean isInterface,
                                  boolean classIsAbstract,
                                  EffectContract typeEffects) {
        pushLocalScope(); // method parameters live here
        try {
            parseClassMemberInner(className, fields, methods, nativeStruct, isInterface, classIsAbstract, typeEffects);
        } finally {
            popLocalScope();
        }
    }

    private void parseClassMemberInner(String className,
                                  List<FieldDeclaration> fields,
                                  List<MethodDeclaration> methods,
                                  boolean nativeStruct,
                                  boolean isInterface,
                                  boolean classIsAbstract,
                                  EffectContract typeEffects) {
        if (isNestedTypeAhead()) {
            nestedTypes.add(parseTypeDeclaration());
            return;
        }
        NativeAnnotation nativeAnnotation = null;
        EffectContract effectContract = typeEffects.merge(EffectContract.empty(peek().location()));
        List<MarkerAnnotation> pendingMemberAnns = new ArrayList<>();
        while (check(TokenType.AT)) {
            ParsedAnnotation ann = parseAnnotation();
            if ("Native".equals(ann.name())) {
                if (nativeAnnotation != null) {
                    reporter.error(ann.location(), "duplicate @Native");
                }
                nativeAnnotation = ann.nativeAnnotation();
            } else if (ann.effectContract() != null) {
                effectContract = effectContract.merge(ann.effectContract());
            } else if (ann.marker() != null) {
                pendingMemberAnns.add(ann.marker());
            } else if (isIgnoredAnnotation(ann)) {
                // accepted for source compatibility
            } else {
                reporter.error(ann.location(),
                        "@" + ann.name() + " is not allowed on class members");
            }
        }
        Visibility memberVis = parseVisibilityModifier();
        boolean isStatic = false;
        boolean isNative = false;
        boolean isVolatile = false;
        boolean isAbstract = false;
        boolean isAsync = false;
        boolean isDefault = false;
        boolean isFinal = false;
        for (int i = 0; i < 10; i++) {
            if (!isStatic && match(TokenType.STATIC)) {
                isStatic = true;
            } else if (!isNative && match(TokenType.NATIVE)) {
                isNative = true;
            } else if (!isVolatile && match(TokenType.VOLATILE)) {
                isVolatile = true;
            } else if (!isAbstract && match(TokenType.ABSTRACT)) {
                isAbstract = true;
            } else if (!isAsync && match(TokenType.ASYNC)) {
                isAsync = true;
            } else if (!isDefault && match(TokenType.DEFAULT)) {
                isDefault = true;
            } else if (!isFinal && match(TokenType.FINAL)) {
                isFinal = true;
            } else {
                break;
            }
        }
        if (isAbstract && isFinal) {
            reporter.error(previous().location(), "illegal combination of modifiers: abstract and final");
        }
        if (isDefault) {
            reporter.error(previous().location(),
                    "default interface methods are not supported in JNative 0.14");
        }

        List<TypeParameterDeclaration> methodTypeParams = parseTypeParameterList();
        for (TypeParameterDeclaration tp : methodTypeParams) {
            knownClassNames.add(tp.name());
        }

        if (isConstructorStart(className)) {
            if (isInterface) {
                reporter.error(peek().location(), "interfaces cannot declare constructors");
            }
            if (nativeStruct) {
                reporter.error(peek().location(),
                        "@NativeStruct cannot declare constructors");
            }
            if (isNative || nativeAnnotation != null) {
                reporter.error(peek().location(), "constructors cannot be native");
            }
            if (isAbstract) {
                reporter.error(peek().location(), "constructors cannot be abstract");
            }
            if (isAsync) {
                reporter.error(peek().location(), "constructors cannot be async");
            }
            if (!methodTypeParams.isEmpty()) {
                reporter.error(peek().location(), "constructors cannot declare type parameters");
            }
            Token name = advance();
            List<Parameter> params = parseParameterList();
            skipThrowsClause();
            BlockStatement body = parseBlock();
            if (isFinal) {
                reporter.error(name.location(), "constructors cannot be final");
            }
            methods.add(new MethodDeclaration(
                    memberVis == Visibility.PUBLIC, false, false, true, false, false, false, List.of(),
                    TypeName.ofVoid(name.location()), false,
                    className, params, body, null, effectContract, memberVis, name.location()));
            return;
        }

        TypeName type = parseType();
        int arrayDims = consumeArrayDims();
        Token name = consume(TokenType.IDENTIFIER, "expected member name");
        arrayDims += consumeArrayDims();
        boolean isArray = arrayDims > 0;
        type = wrapArrayDims(type, arrayDims - 1);

        if (check(TokenType.LPAREN)) {
            if (nativeStruct) {
                reporter.error(name.location(),
                        "@NativeStruct cannot declare methods");
            }
            List<Parameter> params = parseParameterList();
            skipThrowsClause();
            BlockStatement body = null;
            boolean methodAbstract = isAbstract || isInterface;
            if (isInterface && isStatic) {
                reporter.error(name.location(), "static interface methods are not supported yet");
            }
            if (isAsync) {
                if (isNative) {
                    reporter.error(name.location(), "async method cannot be native");
                }
                if (isAbstract || methodAbstract) {
                    reporter.error(name.location(), "async method cannot be abstract");
                }
                if (isStatic && type.isVoid()) {
                    reporter.error(name.location(), "async method must return a value (not void)");
                }
            }
            if (isNative) {
                if (check(TokenType.LBRACE)) {
                    body = parseBlock();
                    reporter.error(name.location(), "native method cannot have a body");
                } else {
                    consume(TokenType.SEMICOLON, "expected ';' after native method declaration");
                }
            } else if (methodAbstract) {
                if (check(TokenType.LBRACE)) {
                    body = parseBlock();
                    reporter.error(name.location(), "abstract method cannot have a body");
                } else {
                    consume(TokenType.SEMICOLON, "expected ';' after abstract method declaration");
                }
                if (!isInterface && !classIsAbstract) {
                    reporter.error(name.location(),
                            "abstract method in non-abstract class '" + className + "'");
                }
            } else {
                if (nativeAnnotation != null) {
                    reporter.error(name.location(), "@Native requires a native method");
                }
                body = parseBlock();
            }
            methods.add(new MethodDeclaration(
                    memberVis == Visibility.PUBLIC, isStatic, isNative, false, methodAbstract && !isNative, isAsync,
                    isFinal && !methodAbstract,
                    methodTypeParams, type, isArray,
                    name.lexeme(), params, body, nativeAnnotation, effectContract, memberVis, name.location()));
            if (!pendingMemberAnns.isEmpty()) {
                methodAnnotations.put(currentTypeFullName + "." + name.lexeme(), List.copyOf(pendingMemberAnns));
            }
            return;
        }

        // field
        // 'final' on fields is a source-level marker; IR treats them as ordinary fields.
        if (isInterface) {
            reporter.error(name.location(),
                    "interface instance fields are not supported (constants deferred)");
        }
        if (isNative || nativeAnnotation != null) {
            reporter.error(name.location(), "fields cannot be native");
        }
        if (isAbstract) {
            reporter.error(name.location(), "fields cannot be abstract");
        }
        Expression initializer = null;
        if (match(TokenType.ASSIGN)) {
            if (isArray && check(TokenType.LBRACE)) {
                initializer = parseArrayInitializer(type);
            } else {
                initializer = parseExpression();
            }
        }
        fields.add(new FieldDeclaration(
                type, isArray, name.lexeme(), memberVis, isStatic, isVolatile, initializer, name.location()));
        if (!pendingMemberAnns.isEmpty()) {
            fieldAnnotations.put(currentTypeFullName + "." + name.lexeme(), List.copyOf(pendingMemberAnns));
        }
        while (match(TokenType.COMMA)) {
            Token n2 = consume(TokenType.IDENTIFIER, "expected field name");
            int dims2 = consumeArrayDims();
            boolean isArray2 = dims2 > 0;
            Expression init2 = null;
            if (match(TokenType.ASSIGN)) {
                if (isArray2 && check(TokenType.LBRACE)) {
                    init2 = parseArrayInitializer(type);
                } else {
                    init2 = parseExpression();
                }
            }
            fields.add(new FieldDeclaration(
                    type, isArray || isArray2, n2.lexeme(), memberVis, isStatic, isVolatile, init2, n2.location()));
        }
        consume(TokenType.SEMICOLON, "expected ';' after field declaration");
    }

    /**
     * Parsed annotation. Exactly one of {@code nativeAnnotation} / {@code effectContract}
     * / {@code marker} may be set (or none for NativeStruct/Stack).
     */
    private record ParsedAnnotation(
            String name,
            NativeAnnotation nativeAnnotation,
            EffectContract effectContract,
            MarkerAnnotation marker,
            io.jnative.diagnostics.SourceLocation location
    ) {
        ParsedAnnotation(String name, NativeAnnotation nativeAnnotation,
                         io.jnative.diagnostics.SourceLocation location) {
            this(name, nativeAnnotation, null, null, location);
        }

        ParsedAnnotation(String name, NativeAnnotation nativeAnnotation,
                         EffectContract effectContract,
                         io.jnative.diagnostics.SourceLocation location) {
            this(name, nativeAnnotation, effectContract, null, location);
        }
    }

    private static boolean isRpcMarkerName(String annName) {
        return annName.equals("Rpc")
                || annName.equals("Authenticated")
                || annName.equals("Role")
                || annName.equals("Permission")
                || annName.equals("RpcTimeout")
                || annName.equals("Idempotent")
                || annName.equals("Sensitive")
                || annName.equals("RpcStream")
                || annName.equals("Valid")
                || annName.equals("RpcError")
                || annName.equals("RpcGet")
                || annName.equals("MaxRequestBytes")
                || isMetaMarkerName(annName);
    }

    /** 0.32 compile-time metaprogramming annotations (COMPILE_TIME retention). */
    private static boolean isMetaMarkerName(String annName) {
        return annName.equals("Json")
                || annName.equals("GenerateJson")
                || annName.equals("JsonField")
                || annName.equals("Validated")
                || annName.equals("NotBlank")
                || annName.equals("Min")
                || annName.equals("Max")
                || annName.equals("Config")
                || annName.equals("Default")
                || annName.equals("Command")
                || annName.equals("Option")
                || annName.equals("Service")
                || annName.equals("Inject")
                || annName.equals("Singleton")
                || annName.equals("RequestScoped")
                || annName.equals("Transient")
                || annName.equals("Entity")
                || annName.equals("Id")
                || annName.equals("Repository")
                || annName.equals("Query")
                || annName.equals("Controller")
                || annName.equals("Get")
                || annName.equals("Post")
                || annName.equals("Put")
                || annName.equals("Delete")
                || annName.equals("Patch")
                || annName.equals("Target")
                || annName.equals("Retention");
    }

    /** Java annotations that carry no meaning for JNative; they are parsed and dropped. */
    private static final java.util.Set<String> IGNORED_ANNOTATIONS = java.util.Set.of(
            "Override", "Deprecated", "SuppressWarnings", "FunctionalInterface", "SafeVarargs");

    private static boolean isIgnoredAnnotation(ParsedAnnotation ann) {
        return IGNORED_ANNOTATIONS.contains(ann.name());
    }

    private ParsedAnnotation parseAnnotation() {
        Token at = consume(TokenType.AT, "expected '@'");
        Token name = consume(TokenType.IDENTIFIER, "expected annotation name");
        String annName = name.lexeme();
        if (IGNORED_ANNOTATIONS.contains(annName)) {
            if (match(TokenType.LPAREN)) {
                int depth = 1;
                while (depth > 0 && !isAtEnd()) {
                    if (check(TokenType.LPAREN)) {
                        depth++;
                    } else if (check(TokenType.RPAREN)) {
                        depth--;
                    }
                    advance();
                }
            }
            return new ParsedAnnotation(annName, null, null, null, at.location());
        }
        if (annName.equals("Native")) {
            java.util.Optional<String> symbol = java.util.Optional.empty();
            java.util.Optional<String> library = java.util.Optional.empty();
            java.util.Optional<String> effects = java.util.Optional.empty();
            if (match(TokenType.LPAREN)) {
                if (!check(TokenType.RPAREN)) {
                    do {
                        Token key = consume(TokenType.IDENTIFIER, "expected annotation attribute");
                        consume(TokenType.ASSIGN, "expected '=' in annotation");
                        if (!check(TokenType.STRING_LITERAL) && !check(TokenType.IDENTIFIER)) {
                            throw error(peek(), "annotation attribute value must be a string or identifier");
                        }
                        Token value = advance();
                        String lit = value.lexeme();
                        if (key.lexeme().equals("symbol")) {
                            symbol = java.util.Optional.of(lit);
                        } else if (key.lexeme().equals("library")) {
                            library = java.util.Optional.of(lit);
                        } else if (key.lexeme().equals("effects")) {
                            effects = java.util.Optional.of(lit);
                        } else {
                            reporter.error(key.location(),
                                    "unknown @Native attribute '" + key.lexeme() + "'");
                        }
                    } while (match(TokenType.COMMA));
                }
                consume(TokenType.RPAREN, "expected ')' after @Native attributes");
            }
            return new ParsedAnnotation(
                    "Native",
                    new NativeAnnotation(symbol, library, effects, at.location()),
                    null,
                    null,
                    at.location());
        }
        if (annName.equals("NoGC") || annName.equals("NoThrow") || annName.equals("NoBlock")
                || annName.equals("Deterministic") || annName.equals("RealtimeSafe")) {
            if (match(TokenType.LPAREN)) {
                if (!check(TokenType.RPAREN)) {
                    reporter.error(peek().location(),
                            "@" + annName + " does not take attributes");
                    while (!check(TokenType.RPAREN) && !isAtEnd()) {
                        advance();
                    }
                }
                consume(TokenType.RPAREN, "expected ')' after @" + annName);
            }
            boolean det = annName.equals("Deterministic") || annName.equals("RealtimeSafe");
            EffectContract ec = new EffectContract(
                    annName.equals("NoGC") || det,
                    annName.equals("NoThrow") || det,
                    annName.equals("NoBlock") || det,
                    det,
                    at.location());
            return new ParsedAnnotation(annName, null, ec, null, at.location());
        }
        if (annName.equals("NativeStruct") || annName.equals("Stack")) {
            if (match(TokenType.LPAREN)) {
                if (!check(TokenType.RPAREN)) {
                    reporter.error(peek().location(),
                            "@" + annName + " does not take attributes");
                    while (!check(TokenType.RPAREN) && !isAtEnd()) {
                        advance();
                    }
                }
                consume(TokenType.RPAREN, "expected ')' after @" + annName);
            }
            return new ParsedAnnotation(annName, null, null, null, at.location());
        }
        if (isRpcMarkerName(annName) || isMetaMarkerName(annName)) {
            MarkerAnnotation marker = parseRpcMarker(annName, at);
            return new ParsedAnnotation(annName, null, null, marker, at.location());
        }
        throw error(name, "unknown annotation '@" + annName + "'");
    }

    private MarkerAnnotation parseRpcMarker(String annName, Token at) {
        if (!match(TokenType.LPAREN)) {
            return new MarkerAnnotation(annName, at.location());
        }
        if (check(TokenType.RPAREN)) {
            consume(TokenType.RPAREN, "expected ')'");
            return new MarkerAnnotation(annName, at.location());
        }
        // @Rpc("/path") or @Rpc(value="/path", version=1) or @Role("ADMIN") or @RpcTimeout(5000)
        if (check(TokenType.STRING_LITERAL)) {
            Token lit = advance();
            String path = lit.lexeme();
            Integer version = null;
            if (match(TokenType.COMMA)) {
                Token key = consume(TokenType.IDENTIFIER, "expected annotation attribute");
                consume(TokenType.ASSIGN, "expected '='");
                Token val = advance();
                if (key.lexeme().equals("version") && val.type() == TokenType.INT_LITERAL) {
                    version = Integer.parseInt(val.lexeme());
                }
            }
            consume(TokenType.RPAREN, "expected ')' after @" + annName);
            if (version != null) {
                return new MarkerAnnotation(annName, Optional.of(path), Optional.of(version), at.location());
            }
            return new MarkerAnnotation(annName, path, at.location());
        }
        if (check(TokenType.INT_LITERAL)) {
            Token lit = advance();
            consume(TokenType.RPAREN, "expected ')' after @" + annName);
            return new MarkerAnnotation(annName, Integer.parseInt(lit.lexeme()), at.location());
        }
        // Named attributes: value=..., version=...
        String stringValue = null;
        Integer intValue = null;
        do {
            Token key = consume(TokenType.IDENTIFIER, "expected annotation attribute");
            consume(TokenType.ASSIGN, "expected '=' in annotation");
            Token val = advance();
            if (key.lexeme().equals("value") || key.lexeme().equals("path")) {
                stringValue = val.lexeme();
            } else if (key.lexeme().equals("version") || key.lexeme().equals("ms")
                    || key.lexeme().equals("bytes")) {
                if (val.type() == TokenType.INT_LITERAL) {
                    intValue = Integer.parseInt(val.lexeme());
                }
            } else if (val.type() == TokenType.STRING_LITERAL) {
                stringValue = val.lexeme();
            } else if (val.type() == TokenType.INT_LITERAL) {
                intValue = Integer.parseInt(val.lexeme());
            }
        } while (match(TokenType.COMMA));
        consume(TokenType.RPAREN, "expected ')' after @" + annName);
        return new MarkerAnnotation(annName,
                Optional.ofNullable(stringValue),
                Optional.ofNullable(intValue),
                at.location());
    }

    /** True when the next member is a nested {@code class}/{@code interface}/{@code enum} declaration. */
    private boolean isNestedTypeAhead() {
        int i = 0;
        while (true) {
            Token t = peekAt(i);
            if (t == null) {
                return false;
            }
            switch (t.type()) {
                case PUBLIC, PRIVATE, PROTECTED, STATIC, ABSTRACT, FINAL -> i++;
                case AT -> {
                    i++;
                    if (peekAt(i) != null && peekAt(i).type() == TokenType.IDENTIFIER) {
                        i++;
                    }
                    if (peekAt(i) != null && peekAt(i).type() == TokenType.LPAREN) {
                        int depth = 0;
                        while (peekAt(i) != null) {
                            TokenType tt = peekAt(i).type();
                            i++;
                            if (tt == TokenType.LPAREN) {
                                depth++;
                            } else if (tt == TokenType.RPAREN && --depth == 0) {
                                break;
                            }
                        }
                    }
                }
                case CLASS, INTERFACE, ENUM -> {
                    return peekAt(i + 1) != null && peekAt(i + 1).type() == TokenType.IDENTIFIER;
                }
                default -> {
                    return false;
                }
            }
        }
    }

    private boolean isConstructorStart(String className) {
        return check(TokenType.IDENTIFIER)
                && peek().lexeme().equals(className)
                && checkNext(TokenType.LPAREN);
    }

    private List<Parameter> parseParameterList() {
        consume(TokenType.LPAREN, "expected '('");
        List<Parameter> params = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            do {
                params.add(parseParameter());
            } while (match(TokenType.COMMA));
        }
        consume(TokenType.RPAREN, "expected ')'");
        for (Parameter prm : params) {
            declareLocal(prm.name(), prm.type(), prm.isArray());
        }
        for (int i = 0; i + 1 < params.size(); i++) {
            if (params.get(i).isVarargs()) {
                reporter.error(params.get(i).location(), "a varargs parameter must be the last parameter");
            }
        }
        return params;
    }

    private Parameter parseParameter() {
        boolean isFinal = match(TokenType.FINAL);
        TypeName type = parseType();
        int arrayDims = consumeArrayDims();
        boolean varargs = match(TokenType.ELLIPSIS);
        if (varargs) {
            arrayDims++;
        }
        Token name = consume(TokenType.IDENTIFIER, "expected parameter name");
        arrayDims += consumeArrayDims();
        boolean isArray = arrayDims > 0;
        type = wrapArrayDims(type, arrayDims - 1);
        return new Parameter(type, name.lexeme(), isArray, isFinal, varargs, name.location());
    }

    /** Consumes {@code [] [] ...} pairs and returns how many were read. */
    private int consumeArrayDims() {
        int n = 0;
        while (check(TokenType.LBRACKET) && peekAt(1) != null && peekAt(1).type() == TokenType.RBRACKET) {
            advance();
            advance();
            n++;
        }
        return n;
    }

    /** Wraps {@code type} in {@code count} array layers ({@code count <= 0} returns it unchanged). */
    private static TypeName wrapArrayDims(TypeName type, int count) {
        TypeName t = type;
        for (int i = 0; i < count; i++) {
            t = TypeName.ofArray(t, type.location());
        }
        return t;
    }

    private TypeName parseType() {
        skipPackageQualifier();
        Token t = peek();
        if (match(TokenType.VOID)) {
            return TypeName.ofVoid(t.location());
        }
        if (match(TokenType.BYTE)) {
            return TypeName.ofByte(t.location());
        }
        if (match(TokenType.SHORT)) {
            return TypeName.ofShort(t.location());
        }
        if (match(TokenType.INT)) {
            return TypeName.ofInt(t.location());
        }
        if (match(TokenType.LONG)) {
            return TypeName.ofLong(t.location());
        }
        if (match(TokenType.FLOAT)) {
            return TypeName.ofFloat(t.location());
        }
        if (match(TokenType.DOUBLE)) {
            return TypeName.ofDouble(t.location());
        }
        if (match(TokenType.BOOLEAN)) {
            return TypeName.ofBoolean(t.location());
        }
        if (match(TokenType.CHAR)) {
            return TypeName.ofChar(t.location());
        }
        if (match(TokenType.IDENTIFIER)) {
            Token id = previous();
            if (id.lexeme().equals("NativePtr") && !check(TokenType.DOT)) {
                TypeName arg = null;
                if (match(TokenType.LT)) {
                    arg = parseNativePtrTypeArgument();
                    consume(TokenType.GT, "expected '>' after NativePtr type argument");
                }
                return TypeName.ofNativePtr(arg, id.location());
            }
            StringBuilder qn = new StringBuilder(id.lexeme());
            while (match(TokenType.DOT)) {
                Token next = consume(TokenType.IDENTIFIER, "expected identifier after '.'");
                qn.append('.').append(next.lexeme());
            }
            List<TypeName> args = List.of();
            if (match(TokenType.LT)) {
                if (!check(TokenType.GT)) {
                    args = parseTypeArgumentList();
                }
                consume(TokenType.GT, "expected '>' after type arguments");
            }
            String qualified = qn.toString();
            if (qualified.equals("Map.Entry")) {
                qualified = "Entry";
            }
            String linkedBase = linkedHashAlias(qualified);
            if (linkedBase != null) {
                qualified = linkedBase;
            }
            return TypeName.ofClass(qualified, args, id.location());
        }
        if (match(TokenType.QUESTION)) {
            return parseWildcardType(previous());
        }
        throw error(t, "expected type");
    }

    private TypeName parseWildcardType(Token question) {
        if (match(TokenType.EXTENDS)) {
            TypeName bound = parseTypeArgument();
            return TypeName.ofWildcard(TypeName.WildcardKind.EXTENDS, bound, question.location());
        }
        if (match(TokenType.IDENTIFIER) && "super".equals(previous().lexeme())) {
            TypeName bound = parseTypeArgument();
            return TypeName.ofWildcard(TypeName.WildcardKind.SUPER, bound, question.location());
        }
        return TypeName.ofWildcard(TypeName.WildcardKind.UNBOUNDED, null, question.location());
    }

    private List<TypeName> parseTypeArgumentList() {
        List<TypeName> args = new ArrayList<>();
        do {
            args.add(parseTypeArgument());
        } while (match(TokenType.COMMA));
        return args;
    }

    /** Type argument: primitive, class, wildcard, or type variable name. */
    private TypeName parseTypeArgument() {
        if (match(TokenType.QUESTION)) {
            return parseWildcardType(previous());
        }
        TypeName t = parseType();
        while (check(TokenType.LBRACKET) && peekAt(1) != null && peekAt(1).type() == TokenType.RBRACKET) {
            Token lb = advance();
            advance();
            t = TypeName.ofArray(t, lb.location());
        }
        return t;
    }

    /** Skip {@code <T, U extends X>} during class-name pre-scan. */
    private void skipTypeParameterTokens() {
        if (!check(TokenType.LT)) {
            return;
        }
        int depth = 0;
        while (!isAtEnd()) {
            if (check(TokenType.LT)) {
                depth++;
            } else if (check(TokenType.GT)) {
                depth--;
                advance();
                if (depth == 0) {
                    return;
                }
                continue;
            }
            advance();
        }
    }

    /** Return index after closing {@code >}, or -1 if malformed. */
    private int indexAfterTypeArguments(int start) {
        if (peekAt(start) == null || peekAt(start).type() != TokenType.LT) {
            return -1;
        }
        int depth = 0;
        int i = start;
        while (true) {
            Token t = peekAt(i);
            if (t == null) {
                return -1;
            }
            if (t.type() == TokenType.LT) {
                depth++;
            } else if (t.type() == TokenType.GT) {
                depth--;
                if (depth == 0) {
                    return i + 1;
                }
            }
            i++;
            if (i > current + 128) {
                return -1;
            }
        }
    }

    /**
     * Type argument of {@code NativePtr<T>}: Void, Byte, Short, Int/Int32, Long, Float,
     * Double, Char, or a class / {@code @NativeStruct} name.
     */
    private TypeName parseNativePtrTypeArgument() {
        Token t = peek();
        if (match(TokenType.BYTE)) {
            return TypeName.ofByte(t.location());
        }
        if (match(TokenType.SHORT)) {
            return TypeName.ofShort(t.location());
        }
        if (match(TokenType.INT)) {
            return TypeName.ofInt(t.location());
        }
        if (match(TokenType.LONG)) {
            return TypeName.ofLong(t.location());
        }
        if (match(TokenType.FLOAT)) {
            return TypeName.ofFloat(t.location());
        }
        if (match(TokenType.DOUBLE)) {
            return TypeName.ofDouble(t.location());
        }
        if (match(TokenType.BOOLEAN)) {
            return TypeName.ofBoolean(t.location());
        }
        if (match(TokenType.CHAR)) {
            return TypeName.ofChar(t.location());
        }
        if (match(TokenType.VOID)) {
            return TypeName.ofVoid(t.location());
        }
        if (match(TokenType.IDENTIFIER)) {
            Token id = previous();
            return switch (id.lexeme()) {
                case "Void" -> TypeName.ofVoid(id.location());
                case "Byte" -> TypeName.ofByte(id.location());
                case "Short" -> TypeName.ofShort(id.location());
                case "Int", "Int32" -> TypeName.ofInt(id.location());
                case "Long" -> TypeName.ofLong(id.location());
                case "Float" -> TypeName.ofFloat(id.location());
                case "Double" -> TypeName.ofDouble(id.location());
                case "Char" -> TypeName.ofChar(id.location());
                case "Boolean" -> TypeName.ofBoolean(id.location());
                default -> TypeName.ofClass(id.lexeme(), id.location());
            };
        }
        throw error(t, "expected NativePtr type argument");
    }

    /**
     * Accept a Java {@code throws A, B.C} clause and ignore it. JNative exceptions are
     * unchecked at compile time, so the clause is documentation only (Java source compatibility).
     */
    private void skipThrowsClause() {
        if (!match(TokenType.THROWS)) {
            return;
        }
        do {
            consume(TokenType.IDENTIFIER, "expected exception type after 'throws'");
            while (match(TokenType.DOT)) {
                consume(TokenType.IDENTIFIER, "expected identifier after '.' in throws clause");
            }
        } while (match(TokenType.COMMA));
    }

    private BlockStatement parseBlock() {
        Token lbrace = consume(TokenType.LBRACE, "expected '{'");
        List<Statement> statements = new ArrayList<>();
        pushLocalScope();
        try {
            while (!check(TokenType.RBRACE) && !isAtEnd()) {
                statements.add(parseStatement());
                drainPendingDecls(statements);
            }
        } finally {
            popLocalScope();
        }
        consume(TokenType.RBRACE, "expected '}'");
        return new BlockStatement(statements, lbrace.location());
    }

    /** Extra declarators of {@code int a = 1, b = 2;} beyond the first; spliced into the enclosing list. */
    private final List<Statement> pendingDecls = new ArrayList<>();

    private void drainPendingDecls(List<Statement> into) {
        into.addAll(pendingDecls);
        pendingDecls.clear();
    }

    private Statement parseStatement() {
        try {
            if (match(TokenType.IF)) {
                return parseIf();
            }
            if (match(TokenType.WHILE)) {
                return parseWhile();
            }
            if (match(TokenType.DO)) {
                return parseDoWhile();
            }
            if (match(TokenType.FOR)) {
                return parseFor();
            }
            if (match(TokenType.SWITCH)) {
                return parseSwitch();
            }
            if (match(TokenType.BREAK)) {
                return parseBreak();
            }
            if (match(TokenType.CONTINUE)) {
                return parseContinue();
            }
            if (match(TokenType.RETURN)) {
                return parseReturn();
            }
            if (match(TokenType.TRY)) {
                return parseTry();
            }
            if (match(TokenType.THROW)) {
                return parseThrow();
            }
            if (check(TokenType.LBRACE)) {
                return parseBlock();
            }
            if (match(TokenType.SEMICOLON)) {
                return new EmptyStatement(previous().location());
            }
            if (check(TokenType.IDENTIFIER) && checkNext(TokenType.COLON)) {
                Token label = advance();
                advance(); // ':'
                return new LabeledStatement(label.lexeme(), parseStatement(), label.location());
            }
            if (switchExprDepth > 0 && check(TokenType.IDENTIFIER) && peek().lexeme().equals("yield")
                    && !checkNext(TokenType.ASSIGN) && !checkNext(TokenType.DOT)
                    && !checkNext(TokenType.PLUS_PLUS) && !checkNext(TokenType.MINUS_MINUS)) {
                Token y = advance();
                Expression value = parseExpression();
                consume(TokenType.SEMICOLON, "expected ';' after yield");
                return new YieldStatement(value, y.location());
            }
            if (check(TokenType.SUPER) && checkNext(TokenType.LPAREN)) {
                return parseConstructorDelegation(true);
            }
            if (check(TokenType.THIS) && checkNext(TokenType.LPAREN)) {
                return parseConstructorDelegation(false);
            }
            if (isVariableDeclarationStart()) {
                return parseVariableDeclaration();
            }
            return parseExpressionStatement();
        } catch (ParseException e) {
            reporter.error(e.location(), e.getMessage());
            synchronize();
            return new EmptyStatement(e.location());
        }
    }

    private TryStatement parseTry() {
        Token start = previous();
        List<VariableDeclaration> resources = List.of();
        BlockStatement tryBlock;
        pushLocalScope();
        try {
            if (match(TokenType.LPAREN)) {
                resources = parseTryResources();
                for (VariableDeclaration r : resources) {
                    declareLocal(r.name(), r.type(), r.isArray());
                }
            }
            tryBlock = parseBlock();
        } finally {
            popLocalScope();
        }
        List<CatchClause> catches = new ArrayList<>();
        while (match(TokenType.CATCH)) {
            consume(TokenType.LPAREN, "expected '(' after 'catch'");
            TypeName catchType = parseType();
            Token varName = consume(TokenType.IDENTIFIER, "expected catch variable name");
            consume(TokenType.RPAREN, "expected ')' after catch parameter");
            BlockStatement catchBody;
            pushLocalScope();
            try {
                declareLocal(varName.lexeme(), catchType, false);
                catchBody = parseBlock();
            } finally {
                popLocalScope();
            }
            catches.add(new CatchClause(catchType, varName.lexeme(), catchBody, start.location()));
        }
        BlockStatement finallyBlock = null;
        if (match(TokenType.FINALLY)) {
            finallyBlock = parseBlock();
        }
        if (resources.isEmpty() && catches.isEmpty() && finallyBlock == null) {
            throw new ParseException(start.location(),
                    "try must have resources, catch, or finally");
        }
        return new TryStatement(resources, tryBlock, catches, finallyBlock, start.location());
    }

    /** Parses {@code Type name = expr (; Type name = expr)* ;?} before the closing {@code )}. */
    private List<VariableDeclaration> parseTryResources() {
        List<VariableDeclaration> resources = new ArrayList<>();
        if (check(TokenType.RPAREN)) {
            consume(TokenType.RPAREN, "expected ')' after try resources");
            return resources;
        }
        while (true) {
            TypeName type = parseType();
            int arrayDims = consumeArrayDims();
            Token name = consume(TokenType.IDENTIFIER, "expected resource variable name");
            arrayDims += consumeArrayDims();
            boolean isArray = arrayDims > 0;
            type = wrapArrayDims(type, arrayDims - 1);
            consume(TokenType.ASSIGN, "try-with-resources variable must be initialized");
            Expression init = parseExpression();
            // TWR resources are implicitly final (Java §14.20.3).
            resources.add(new VariableDeclaration(
                    type, isArray, name.lexeme(), init, false, true, name.location()));
            if (match(TokenType.SEMICOLON)) {
                if (check(TokenType.RPAREN)) {
                    break;
                }
                continue;
            }
            break;
        }
        consume(TokenType.RPAREN, "expected ')' after try resources");
        return resources;
    }

    private ThrowStatement parseThrow() {
        Token start = previous();
        Expression expr = parseExpression();
        consume(TokenType.SEMICOLON, "expected ';' after throw expression");
        return new ThrowStatement(expr, start.location());
    }

    private ConstructorDelegationStatement parseConstructorDelegation(boolean isSuper) {
        Token keyword = advance();
        consume(TokenType.LPAREN, "expected '(' after '" + keyword.lexeme() + "'");
        List<Expression> args = parseArgumentList();
        consume(TokenType.RPAREN, "expected ')' after arguments");
        consume(TokenType.SEMICOLON, "expected ';' after constructor delegation");
        return new ConstructorDelegationStatement(isSuper, args, keyword.location());
    }

    private boolean isVariableDeclarationStart() {
        int offset = 0;
        if (check(TokenType.FINAL)) {
            offset = 1;
        }
        return isVariableDeclarationStartAt(offset);
    }

    private boolean isVariableDeclarationStartAt(int offset) {
        Token t0 = peekAt(offset);
        if (t0 == null) {
            return false;
        }
        if (t0.type() == TokenType.AT
                && peekAt(offset + 1) != null
                && peekAt(offset + 1).type() == TokenType.IDENTIFIER
                && "Stack".equals(peekAt(offset + 1).lexeme())) {
            return true;
        }
        if (isPrimitiveTypeToken(t0)) {
            return true;
        }
        if (t0.type() == TokenType.IDENTIFIER && "NativePtr".equals(t0.lexeme())) {
            return isNativePtrVariableDeclAhead(offset);
        }
        if (t0.type() == TokenType.IDENTIFIER) {
            int afterType = indexAfterQualifiedTypeName(offset);
            if (afterType < 0) {
                return false;
            }
            // Simple name must be a known type; FQN (contains '.') is accepted structurally.
            boolean fqn = afterType > offset + 1;
            if (!fqn && !knownClassNames.contains(t0.lexeme())) {
                return false;
            }
            int afterArgs = indexAfterTypeArguments(afterType);
            if (afterArgs >= 0) {
                afterType = afterArgs;
            }
            Token next = peekAt(afterType);
            if (next != null && next.type() == TokenType.IDENTIFIER) {
                return true;
            }
            if (next != null && next.type() == TokenType.LBRACKET) {
                int k = afterType;
                while (peekAt(k) != null && peekAt(k).type() == TokenType.LBRACKET
                        && peekAt(k + 1) != null && peekAt(k + 1).type() == TokenType.RBRACKET) {
                    k += 2;
                }
                Token name = peekAt(k);
                return k > afterType && name != null && name.type() == TokenType.IDENTIFIER;
            }
        }
        return false;
    }

    /** Index after {@code ID (. ID)*} starting at {@code start}, or -1. */
    private int indexAfterQualifiedTypeName(int start) {
        Token first = peekAt(start);
        if (first == null || first.type() != TokenType.IDENTIFIER) {
            return -1;
        }
        int i = start + 1;
        while (peekAt(i) != null && peekAt(i).type() == TokenType.DOT
                && peekAt(i + 1) != null && peekAt(i + 1).type() == TokenType.IDENTIFIER) {
            i += 2;
        }
        return i;
    }

    /** {@code NativePtr p} / {@code NativePtr<T> p} / optional {@code []}. */
    private boolean isNativePtrVariableDeclAhead() {
        return isNativePtrVariableDeclAhead(0);
    }

    private boolean isNativePtrVariableDeclAhead(int offset) {
        int i = offset + 1;
        if (peekAt(i) != null && peekAt(i).type() == TokenType.LT) {
            int depth = 0;
            while (true) {
                Token t = peekAt(i);
                if (t == null) {
                    return false;
                }
                if (t.type() == TokenType.LT) {
                    depth++;
                } else if (t.type() == TokenType.GT) {
                    depth--;
                    if (depth == 0) {
                        i++;
                        break;
                    }
                }
                i++;
                if (i > offset + 64) {
                    return false;
                }
            }
        }
        Token next = peekAt(i);
        if (next == null) {
            return false;
        }
        if (next.type() == TokenType.IDENTIFIER) {
            return true;
        }
        if (next.type() == TokenType.LBRACKET) {
            int k = i;
            while (peekAt(k) != null && peekAt(k).type() == TokenType.LBRACKET
                    && peekAt(k + 1) != null && peekAt(k + 1).type() == TokenType.RBRACKET) {
                k += 2;
            }
            Token name = peekAt(k);
            return k > i && name != null && name.type() == TokenType.IDENTIFIER;
        }
        return false;
    }

    private boolean isPrimitiveTypeToken(Token token) {
        return switch (token.type()) {
            case BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BOOLEAN, CHAR -> true;
            default -> false;
        };
    }

    private static boolean isVarKeyword(TypeName t) {
        return t != null && t.kind() == TypeName.Kind.CLASS && "var".equals(t.name());
    }

    private VariableDeclaration parseVariableDeclaration() {
        boolean isFinal = match(TokenType.FINAL);
        boolean stackAllocated = false;
        if (check(TokenType.AT)) {
            ParsedAnnotation ann = parseAnnotation();
            if ("Stack".equals(ann.name())) {
                stackAllocated = true;
            } else if (isIgnoredAnnotation(ann)) {
                // accepted for source compatibility
            } else {
                reporter.error(ann.location(),
                        "@" + ann.name() + " is not allowed on local variables");
            }
        }
        TypeName type = parseType();
        TypeName baseType = type;
        int baseDims = consumeArrayDims();
        int arrayDims = baseDims;
        Token name = consume(TokenType.IDENTIFIER, "expected variable name");
        arrayDims += consumeArrayDims();
        boolean isArray = arrayDims > 0;
        type = wrapArrayDims(type, arrayDims - 1);
        Expression init = null;
        if (match(TokenType.ASSIGN)) {
            if (isArray && check(TokenType.LBRACE)) {
                init = parseArrayInitializer(type);
            } else {
                init = parseExpression();
            }
        }
        VariableDeclaration first = new VariableDeclaration(
                type, isArray, name.lexeme(), init, stackAllocated, isFinal, name.location());
        declareLocal(name.lexeme(), isVarKeyword(type) ? inferLocalType(init) : type, isVarKeyword(type) ? false : isArray);
        while (match(TokenType.COMMA)) {
            Token n2 = consume(TokenType.IDENTIFIER, "expected variable name");
            int dims2 = baseDims + consumeArrayDims();
            boolean isArray2 = dims2 > 0;
            TypeName t2 = wrapArrayDims(baseType, dims2 - 1);
            Expression init2 = null;
            if (match(TokenType.ASSIGN)) {
                if (isArray2 && check(TokenType.LBRACE)) {
                    init2 = parseArrayInitializer(t2);
                } else {
                    init2 = parseExpression();
                }
            }
            pendingDecls.add(new VariableDeclaration(
                    t2, isArray2, n2.lexeme(), init2, stackAllocated, isFinal, n2.location()));
            declareLocal(n2.lexeme(), t2, isArray2);
        }
        consume(TokenType.SEMICOLON, "expected ';' after variable declaration");
        return first;
    }

    private IfStatement parseIf() {
        Token ifTok = previous();
        consume(TokenType.LPAREN, "expected '(' after if");
        Expression condition = parseExpression();
        consume(TokenType.RPAREN, "expected ')' after if condition");
        Statement thenBranch = parseStatement();
        Statement elseBranch = null;
        if (match(TokenType.ELSE)) {
            elseBranch = parseStatement();
        }
        return new IfStatement(condition, thenBranch, elseBranch, ifTok.location());
    }

    private WhileStatement parseWhile() {
        Token whileTok = previous();
        consume(TokenType.LPAREN, "expected '(' after while");
        Expression condition = parseExpression();
        consume(TokenType.RPAREN, "expected ')' after while condition");
        Statement body = parseStatement();
        return new WhileStatement(condition, body, whileTok.location());
    }

    private DoWhileStatement parseDoWhile() {
        Token doTok = previous();
        Statement body = parseStatement();
        consume(TokenType.WHILE, "expected 'while' after do body");
        consume(TokenType.LPAREN, "expected '(' after while");
        Expression condition = parseExpression();
        consume(TokenType.RPAREN, "expected ')' after do-while condition");
        consume(TokenType.SEMICOLON, "expected ';' after do-while");
        return new DoWhileStatement(body, condition, doTok.location());
    }

    /** Nesting depth of switch expressions being parsed ({@code yield} is a statement only inside). */
    private int switchExprDepth;
    /** While parsing a case label, {@code NAME ->} is the label arrow, not a lambda. */
    private boolean inCaseLabel;

    /** {@code switch (x) { ... }} as a statement: classic ({@code case X:}) or arrow form. */
    private SwitchStatement parseSwitch() {
        Token sw = previous();
        Expression selector = parseSwitchSelector();
        List<SwitchSection> sections = parseSwitchBody(false);
        return new SwitchStatement(selector, sections, sw.location());
    }

    /** {@code switch (x) { case A -> v; ... }} as an expression. */
    private SwitchExpression parseSwitchExpression() {
        Token sw = previous();
        Expression selector = parseSwitchSelector();
        switchExprDepth++;
        List<SwitchSection> sections;
        try {
            sections = parseSwitchBody(true);
        } finally {
            switchExprDepth--;
        }
        return new SwitchExpression(selector, sections, sw.location());
    }

    private Expression parseSwitchSelector() {
        consume(TokenType.LPAREN, "expected '(' after 'switch'");
        Expression selector = parseExpression();
        consume(TokenType.RPAREN, "expected ')' after switch selector");
        return selector;
    }

    private List<SwitchSection> parseSwitchBody(boolean asExpression) {
        consume(TokenType.LBRACE, "expected '{' after switch selector");
        pushLocalScope();
        try {
            return parseSwitchSections(asExpression);
        } finally {
            popLocalScope();
        }
    }

    private List<SwitchSection> parseSwitchSections(boolean asExpression) {
        List<SwitchSection> sections = new ArrayList<>();
        Boolean arrowForm = null;
        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            if (!check(TokenType.CASE) && !check(TokenType.DEFAULT)) {
                throw error(peek(), "expected 'case' or 'default' in switch");
            }
            List<SwitchLabel> labels = new ArrayList<>();
            SourceLocation sectionLoc = peek().location();
            boolean arrow = false;
            while (check(TokenType.CASE) || check(TokenType.DEFAULT)) {
                if (match(TokenType.CASE)) {
                    Token caseTok = previous();
                    do {
                        boolean saved = inCaseLabel;
                        inCaseLabel = true;
                        Expression constant;
                        try {
                            constant = parseTernary();
                        } finally {
                            inCaseLabel = saved;
                        }
                        labels.add(new CaseLabel(constant, caseTok.location()));
                    } while (match(TokenType.COMMA));
                } else {
                    Token defTok = consume(TokenType.DEFAULT, "expected 'default'");
                    labels.add(new DefaultLabel(defTok.location()));
                }
                if (match(TokenType.ARROW)) {
                    arrow = true;
                    break;
                }
                consume(TokenType.COLON, "expected ':' or '->' after case label");
            }
            if (arrowForm != null && arrowForm != arrow) {
                throw error(previous(), "cannot mix 'case X:' and 'case X ->' in one switch");
            }
            arrowForm = arrow;
            List<Statement> stmts = new ArrayList<>();
            if (arrow) {
                if (check(TokenType.LBRACE)) {
                    stmts.add(parseBlock());
                } else if (check(TokenType.THROW)) {
                    advance();
                    stmts.add(parseThrow());
                } else {
                    Expression value = parseExpression();
                    consume(TokenType.SEMICOLON, "expected ';' after switch arm");
                    stmts.add(asExpression
                            ? new YieldStatement(value, value.location())
                            : new ExpressionStatement(stripPostfix(value), value.location()));
                }
            } else {
                while (!check(TokenType.CASE) && !check(TokenType.DEFAULT)
                        && !check(TokenType.RBRACE) && !isAtEnd()) {
                    stmts.add(parseStatement());
                    drainPendingDecls(stmts);
                }
            }
            sections.add(new SwitchSection(labels, stmts, arrow, sectionLoc));
        }
        consume(TokenType.RBRACE, "expected '}' after switch body");
        return sections;
    }

    private BreakStatement parseBreak() {
        Token tok = previous();
        String label = check(TokenType.IDENTIFIER) ? advance().lexeme() : null;
        consume(TokenType.SEMICOLON, "expected ';' after break");
        return new BreakStatement(label, tok.location());
    }

    private ContinueStatement parseContinue() {
        Token tok = previous();
        String label = check(TokenType.IDENTIFIER) ? advance().lexeme() : null;
        consume(TokenType.SEMICOLON, "expected ';' after continue");
        return new ContinueStatement(label, tok.location());
    }

    private Statement parseFor() {
        pushLocalScope();
        try {
            return parseForInner();
        } finally {
            popLocalScope();
        }
    }

    private Statement parseForInner() {
        Token forTok = previous();
        consume(TokenType.LPAREN, "expected '(' after for");

        if (isEnhancedForAhead()) {
            return parseEnhancedFor(forTok);
        }

        Statement initializer;
        List<Statement> prelude = new ArrayList<>();
        if (match(TokenType.SEMICOLON)) {
            initializer = null;
        } else if (isVariableDeclarationStart()) {
            initializer = parseVariableDeclaration();
            drainPendingDecls(prelude);
        } else {
            Expression expr = parseExpression();
            initializer = new ExpressionStatement(stripPostfix(expr), expr.location());
            while (match(TokenType.COMMA)) {
                Expression more = parseExpression();
                prelude.add(new ExpressionStatement(stripPostfix(more), more.location()));
            }
            consume(TokenType.SEMICOLON, "expected ';' after for initializer");
        }

        Expression condition = null;
        if (!check(TokenType.SEMICOLON)) {
            condition = parseExpression();
        }
        consume(TokenType.SEMICOLON, "expected ';' after for condition");

        Expression update = null;
        List<Expression> moreUpdates = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            update = stripPostfix(parseExpression());
            while (match(TokenType.COMMA)) {
                moreUpdates.add(stripPostfix(parseExpression()));
            }
        }
        consume(TokenType.RPAREN, "expected ')' after for clauses");
        Statement body = parseStatement();
        ForStatement loop = new ForStatement(initializer, condition, update, body, forTok.location(), moreUpdates);
        if (prelude.isEmpty()) {
            return loop;
        }
        // for (int i = 0, j = 1; ...) -> { int i = 0; int j = 1; for (; ...) }  (same scoping)
        List<Statement> stmts = new ArrayList<>();
        if (initializer != null) {
            stmts.add(initializer);
        }
        stmts.addAll(prelude);
        stmts.add(new ForStatement(null, condition, update, body, forTok.location(), moreUpdates));
        return new BlockStatement(stmts, forTok.location());
    }

    /**
     * Lookahead for {@code for ( [final] Type name : expr )}.
     * Rejects Iterable-style when later checked semantically; here only syntax shape.
     */
    private boolean isEnhancedForAhead() {
        int offset = 0;
        if (peekAt(offset) != null && peekAt(offset).type() == TokenType.FINAL) {
            offset++;
        }
        if (!isVariableDeclarationStartAt(offset)) {
            return false;
        }
        // Walk type + optional [] + name, then require ':'
        if (peekAt(offset) != null && peekAt(offset).type() == TokenType.AT) {
            return false; // @Stack not used in enhanced-for
        }
        int afterType;
        Token t0 = peekAt(offset);
        if (t0 != null && isPrimitiveTypeToken(t0)) {
            afterType = offset + 1;
        } else if (t0 != null && t0.type() == TokenType.IDENTIFIER) {
            afterType = indexAfterQualifiedTypeName(offset);
            if (afterType < 0) {
                return false;
            }
            int afterArgs = indexAfterTypeArguments(afterType);
            if (afterArgs >= 0) {
                afterType = afterArgs;
            }
        } else {
            return false;
        }
        while (peekAt(afterType) != null && peekAt(afterType).type() == TokenType.LBRACKET
                && peekAt(afterType + 1) != null && peekAt(afterType + 1).type() == TokenType.RBRACKET) {
            afterType += 2;
        }
        Token name = peekAt(afterType);
        if (name == null || name.type() != TokenType.IDENTIFIER) {
            return false;
        }
        Token colon = peekAt(afterType + 1);
        return colon != null && colon.type() == TokenType.COLON;
    }

    private EnhancedForStatement parseEnhancedFor(Token forTok) {
        boolean isFinal = match(TokenType.FINAL);
        TypeName type = parseType();
        int arrayDims = consumeArrayDims();
        Token name = consume(TokenType.IDENTIFIER, "expected loop variable name");
        arrayDims += consumeArrayDims();
        boolean isArray = arrayDims > 0;
        type = wrapArrayDims(type, arrayDims - 1);
        consume(TokenType.COLON, "expected ':' in enhanced for");
        Expression array = parseExpression();
        declareLocal(name.lexeme(), isVarKeyword(type) ? null : type, isVarKeyword(type) ? false : isArray);
        consume(TokenType.RPAREN, "expected ')' after enhanced for");
        Statement body = parseStatement();
        return new EnhancedForStatement(
                type, isArray, isFinal, name.lexeme(), array, body, forTok.location());
    }

    private ReturnStatement parseReturn() {
        Token ret = previous();
        Expression value = null;
        if (!check(TokenType.SEMICOLON)) {
            value = parseExpression();
        }
        consume(TokenType.SEMICOLON, "expected ';' after return");
        return new ReturnStatement(value, ret.location());
    }

    /** A postfix increment whose value is discarded needs no old-value result. */
    private static Expression stripPostfix(Expression expr) {
        return expr instanceof AssignmentExpression a ? a.asStatement() : expr;
    }

    private ExpressionStatement parseExpressionStatement() {
        Expression expr = parseExpression();
        consume(TokenType.SEMICOLON, "expected ';' after expression");
        return new ExpressionStatement(stripPostfix(expr), expr.location());
    }

    private Expression parseExpression() {
        return parseAssignment();
    }

    private Expression parseAssignment() {
        Expression expr = parseTernary();
        if (match(TokenType.ASSIGN)) {
            Token eq = previous();
            Expression value = parseAssignment();
            if (isAssignable(expr)) {
                return new AssignmentExpression(expr, value, eq.location());
            }
            throw error(eq, "invalid assignment target");
        }
        if (check(TokenType.COMPOUND_ASSIGN)) {
            Token opTok = advance();
            Expression value = parseAssignment();
            if (!isAssignable(expr)) {
                throw error(opTok, "invalid assignment target");
            }
            if (!isSideEffectFree(expr)) {
                throw error(opTok, "compound assignment target must not contain calls, assignments or "
                        + "increments (the target would be evaluated twice)");
            }
            BinaryOp bop = switch (opTok.lexeme()) {
                case "+=" -> BinaryOp.ADD;
                case "-=" -> BinaryOp.SUB;
                case "*=" -> BinaryOp.MUL;
                case "/=" -> BinaryOp.DIV;
                case "%=" -> BinaryOp.MOD;
                case "&=" -> BinaryOp.BIT_AND;
                case "|=" -> BinaryOp.BIT_OR;
                case "^=" -> BinaryOp.BIT_XOR;
                case "<<=" -> BinaryOp.SHL;
                case ">>=" -> BinaryOp.SHR;
                case ">>>=" -> BinaryOp.USHR;
                default -> throw error(opTok, "unsupported compound assignment " + opTok.lexeme());
            };
            // a op= b  ==>  a = (T)(a op (b)); T is the type of a (resolved in the semantic pass).
            Expression combined = new BinaryExpression(
                    expr, bop, new GroupingExpression(value, value.location()), opTok.location());
            Expression narrowed = new CastExpression(
                    TypeName.ofClass(COMPOUND_CAST_MARKER, opTok.location()), combined, opTok.location());
            return new AssignmentExpression(expr, narrowed, opTok.location());
        }
        return expr;
    }

    /** Marker type name for the implicit narrowing cast of a desugared compound assignment. */
    public static final String COMPOUND_CAST_MARKER = "$compound";

    /** True when evaluating {@code expr} twice is indistinguishable from evaluating it once. */
    private static boolean isSideEffectFree(Expression expr) {
        return switch (expr) {
            case VariableExpression ignored -> true;
            case LiteralExpression ignored -> true;
            case ThisExpression ignored -> true;
            case GroupingExpression g -> isSideEffectFree(g.expression());
            case FieldAccessExpression f -> f.receiver() == null || isSideEffectFree(f.receiver());
            case ArrayAccessExpression a -> isSideEffectFree(a.array()) && isSideEffectFree(a.index());
            case UnaryExpression u -> isSideEffectFree(u.operand());
            case BinaryExpression b -> isSideEffectFree(b.left()) && isSideEffectFree(b.right());
            default -> false;
        };
    }
    private boolean isAssignable(Expression expr) {
        return expr instanceof VariableExpression
                || expr instanceof FieldAccessExpression
                || expr instanceof ArrayAccessExpression;
    }

    /** {@code cond ? then : else} — right-associative; middle is a full expression. */
    private Expression parseTernary() {
        Expression expr = parseOr();
        if (match(TokenType.QUESTION)) {
            Token q = previous();
            Expression thenExpr = parseExpression();
            consume(TokenType.COLON, "expected ':' in conditional expression");
            Expression elseExpr = parseTernary();
            return new ConditionalExpression(expr, thenExpr, elseExpr, q.location());
        }
        return expr;
    }

    private Expression parseOr() {
        Expression expr = parseAnd();
        while (match(TokenType.OR)) {
            Token op = previous();
            Expression right = parseAnd();
            expr = new BinaryExpression(expr, BinaryOp.OR, right, op.location());
        }
        return expr;
    }

    private Expression parseAnd() {
        Expression expr = parseBitOr();
        while (match(TokenType.AND)) {
            Token op = previous();
            Expression right = parseBitOr();
            expr = new BinaryExpression(expr, BinaryOp.AND, right, op.location());
        }
        return expr;
    }

    private Expression parseBitOr() {
        Expression expr = parseBitXor();
        while (match(TokenType.BIT_OR)) {
            Token op = previous();
            Expression right = parseBitXor();
            expr = new BinaryExpression(expr, BinaryOp.BIT_OR, right, op.location());
        }
        return expr;
    }

    private Expression parseBitXor() {
        Expression expr = parseBitAnd();
        while (match(TokenType.BIT_XOR)) {
            Token op = previous();
            Expression right = parseBitAnd();
            expr = new BinaryExpression(expr, BinaryOp.BIT_XOR, right, op.location());
        }
        return expr;
    }

    private Expression parseBitAnd() {
        Expression expr = parseEquality();
        while (match(TokenType.BIT_AND)) {
            Token op = previous();
            Expression right = parseEquality();
            expr = new BinaryExpression(expr, BinaryOp.BIT_AND, right, op.location());
        }
        return expr;
    }

    private Expression parseEquality() {
        Expression expr = parseRelational();
        while (match(TokenType.EQ, TokenType.NE)) {
            Token op = previous();
            Expression right = parseRelational();
            BinaryOp bop = op.type() == TokenType.EQ ? BinaryOp.EQ : BinaryOp.NE;
            expr = new BinaryExpression(expr, bop, right, op.location());
        }
        return expr;
    }

    private Expression parseRelational() {
        Expression expr = parseShift();
        while (true) {
            if (match(TokenType.INSTANCEOF)) {
                Token op = previous();
                match(TokenType.FINAL);
                TypeName type = parseType();
                String binding = check(TokenType.IDENTIFIER) ? advance().lexeme() : null;
                if (binding != null) {
                    declareLocal(binding, type, false);
                }
                expr = new InstanceOfExpression(expr, type, op.location(), binding);
                continue;
            }
            if (match(TokenType.LT, TokenType.LE, TokenType.GT, TokenType.GE)) {
                Token op = previous();
                Expression right = parseShift();
                BinaryOp bop = switch (op.type()) {
                    case LT -> BinaryOp.LT;
                    case LE -> BinaryOp.LE;
                    case GT -> BinaryOp.GT;
                    case GE -> BinaryOp.GE;
                    default -> throw new IllegalStateException();
                };
                expr = new BinaryExpression(expr, bop, right, op.location());
                continue;
            }
            break;
        }
        return expr;
    }

    /**
     * Shift operators assembled from {@code <}/{@code >} tokens so nested generics
     * {@code List<List<T>>} keep separate {@code >} tokens.
     */
    private Expression parseShift() {
        Expression expr = parseAdditive();
        while (true) {
            if (check(TokenType.LT) && peekAt(1) != null && peekAt(1).type() == TokenType.LT) {
                Token op = advance();
                advance(); // second <
                Expression right = parseAdditive();
                expr = new BinaryExpression(expr, BinaryOp.SHL, right, op.location());
                continue;
            }
            if (check(TokenType.GT) && peekAt(1) != null && peekAt(1).type() == TokenType.GT) {
                Token op = advance();
                advance(); // second >
                BinaryOp bop = BinaryOp.SHR;
                if (check(TokenType.GT)) {
                    advance();
                    bop = BinaryOp.USHR;
                }
                Expression right = parseAdditive();
                expr = new BinaryExpression(expr, bop, right, op.location());
                continue;
            }
            break;
        }
        return expr;
    }

    private Expression parseAdditive() {
        Expression expr = parseMultiplicative();
        while (match(TokenType.PLUS, TokenType.MINUS)) {
            Token op = previous();
            Expression right = parseMultiplicative();
            BinaryOp bop = op.type() == TokenType.PLUS ? BinaryOp.ADD : BinaryOp.SUB;
            expr = new BinaryExpression(expr, bop, right, op.location());
        }
        return expr;
    }

    private Expression parseMultiplicative() {
        Expression expr = parseUnary();
        while (match(TokenType.STAR, TokenType.SLASH, TokenType.PERCENT)) {
            Token op = previous();
            Expression right = parseUnary();
            BinaryOp bop = switch (op.type()) {
                case STAR -> BinaryOp.MUL;
                case SLASH -> BinaryOp.DIV;
                case PERCENT -> BinaryOp.MOD;
                default -> throw new IllegalStateException();
            };
            expr = new BinaryExpression(expr, bop, right, op.location());
        }
        return expr;
    }

    /**
     * {@code x++ / x-- / ++x / --x}  ==>  {@code x = (T)(x +/- 1)} (T = type of x, resolved semantically).
     * The target is evaluated more than once, so it must be free of side effects.
     */
    private Expression incrementOf(Expression target, Token op, boolean postfix) {
        if (!isAssignable(target)) {
            throw error(op, "invalid increment/decrement target");
        }
        if (!isSideEffectFree(target)) {
            throw error(op, "increment/decrement target must not contain calls, assignments or "
                    + "increments (the target would be evaluated twice)");
        }
        BinaryOp bop = op.type() == TokenType.PLUS_PLUS ? BinaryOp.ADD : BinaryOp.SUB;
        Expression one = LiteralExpression.ofInt(1, op.location());
        Expression combined = new BinaryExpression(target, bop, one, op.location());
        Expression narrowed = new CastExpression(
                TypeName.ofClass(COMPOUND_CAST_MARKER, op.location()), combined, op.location());
        return new AssignmentExpression(target, narrowed, postfix, op.location());
    }

    private Expression parseUnary() {
        if (match(TokenType.PLUS_PLUS) || match(TokenType.MINUS_MINUS)) {
            Token op = previous();
            return incrementOf(parseUnary(), op, false);
        }
        if (match(TokenType.NOT)) {
            Token op = previous();
            return new UnaryExpression(UnaryOp.NOT, parseUnary(), op.location());
        }
        if (match(TokenType.BIT_NOT)) {
            Token op = previous();
            return new UnaryExpression(UnaryOp.BIT_NOT, parseUnary(), op.location());
        }
        if (match(TokenType.MINUS)) {
            Token op = previous();
            // The most negative values are only representable as a negated literal: -2147483648, -9223372036854775808L.
            if (check(TokenType.INT_LITERAL) && peek().lexeme().equals("2147483648")) {
                advance();
                return LiteralExpression.ofInt(Integer.MIN_VALUE, op.location());
            }
            if (check(TokenType.LONG_LITERAL) && peek().lexeme().equals("9223372036854775808L")) {
                advance();
                return LiteralExpression.ofLong(Long.MIN_VALUE, op.location());
            }
            return new UnaryExpression(UnaryOp.NEGATE, parseUnary(), op.location());
        }
        if (match(TokenType.PLUS)) {
            Token op = previous();
            return new UnaryExpression(UnaryOp.PLUS, parseUnary(), op.location());
        }
        if (match(TokenType.AWAIT)) {
            Token op = previous();
            return new AwaitExpression(parseUnary(), op.location());
        }
        return parsePostfix();
    }

    private Expression parsePostfix() {
        Expression expr = parsePrimary();
        while (true) {
            if (match(TokenType.DOT)) {
                Token name = consumeMemberNameAfterDot();
                List<TypeName> typeArgs = List.of();
                if (isMethodTypeArgsAhead()) {
                    typeArgs = parseMethodTypeArguments();
                }
                if (match(TokenType.LPAREN)) {
                    List<Expression> args = parseArgumentList();
                    consume(TokenType.RPAREN, "expected ')' after arguments");
                    if (expr instanceof VariableExpression sv && sv.name().equals("String")
                            && name.lexeme().equals("format")) {
                        expr = new VariableExpression("JnFormat", sv.location());
                    }
                    expr = new MethodCallExpression(
                            expr, name.lexeme(), args, typeArgs, name.location());
                } else if (!typeArgs.isEmpty()) {
                    throw error(peek(), "expected '(' after type arguments");
                } else if (expr instanceof VariableExpression v
                        && v.name().equals("NativePtr")
                        && name.lexeme().equals("NULL")) {
                    expr = new NativeNullExpression(name.location());
                } else {
                    expr = new FieldAccessExpression(expr, name.lexeme(), name.location());
                }
                continue;
            }
            if (match(TokenType.LBRACKET)) {
                Token lbracket = previous();
                Expression index = parseExpression();
                consume(TokenType.RBRACKET, "expected ']' after array index");
                expr = new ArrayAccessExpression(expr, index, lbracket.location());
                continue;
            }
            if (match(TokenType.PLUS_PLUS) || match(TokenType.MINUS_MINUS)) {
                expr = incrementOf(expr, previous(), true);
                continue;
            }
            if (match(TokenType.COLON_COLON)) {
                Token methodTok;
                if (match(TokenType.NEW)) {
                    // Type::new — accepted in parse, rejected in semantic (not supported yet).
                    methodTok = previous();
                } else {
                    methodTok = consume(TokenType.IDENTIFIER, "expected method name after '::'");
                }
                if (expr instanceof VariableExpression v && knownClassNames.contains(v.name())) {
                    expr = new MethodReferenceExpression(
                            TypeName.ofClass(v.name(), v.location()), null, methodTok.lexeme(),
                            methodTok.location());
                } else {
                    expr = new MethodReferenceExpression(null, expr, methodTok.lexeme(), methodTok.location());
                }
                continue;
            }
            break;
        }
        return expr;
    }

    /** Skips a leading {@code java.util.} style package qualifier in an expression such as {@code java.util.Arrays.sort(a)}. */
    private void skipPackageQualifier() {
        if (!check(TokenType.IDENTIFIER)) {
            return;
        }
        String first = peek().lexeme();
        if (!first.equals("java") && !first.equals("javax")) {
            return;
        }
        int k = 0;
        while (peekAt(k) != null && peekAt(k).type() == TokenType.IDENTIFIER
                && peekAt(k + 1) != null && peekAt(k + 1).type() == TokenType.DOT) {
            String seg = peekAt(k).lexeme();
            if (Character.isUpperCase(seg.charAt(0))) {
                break;
            }
            k += 2;
        }
        Token cls = peekAt(k);
        if (k >= 4 && cls != null && cls.type() == TokenType.IDENTIFIER && Character.isUpperCase(cls.lexeme().charAt(0))) {
            for (int i = 0; i < k; i++) {
                advance();
            }
        }
    }

    private Expression parsePrimary() {
        skipPackageQualifier();
        if (match(TokenType.TRUE)) {
            return LiteralExpression.ofBoolean(true, previous().location());
        }
        if (match(TokenType.FALSE)) {
            return LiteralExpression.ofBoolean(false, previous().location());
        }
        if (match(TokenType.NULL)) {
            return new NullLiteralExpression(previous().location());
        }
        if (match(TokenType.THIS)) {
            return new ThisExpression(previous().location());
        }
        if (match(TokenType.SWITCH)) {
            return parseSwitchExpression();
        }
        if (check(TokenType.SUPER)) {
            Token superTok = advance();
            if (!check(TokenType.DOT)) {
                throw error(superTok,
                        "'super' is only supported as 'super(...)' or 'super.method(...)'");
            }
            return new SuperExpression(superTok.location());
        }
        if (match(TokenType.INT_LITERAL)) {
            Token t = previous();
            try {
                return LiteralExpression.ofInt(Integer.parseInt(t.lexeme()), t.location());
            } catch (NumberFormatException e) {
                // 2147483648 is only legal as the operand of unary minus (handled in parseUnary).
                throw error(t, "integer number too large: " + t.lexeme());
            }
        }
        if (match(TokenType.CHAR_LITERAL)) {
            Token t = previous();
            return LiteralExpression.ofChar(Integer.parseInt(t.lexeme()), t.location());
        }
        if (match(TokenType.LONG_LITERAL)) {
            Token t = previous();
            String lex = t.lexeme();
            if (lex.endsWith("L") || lex.endsWith("l")) {
                lex = lex.substring(0, lex.length() - 1);
            }
            try {
                return LiteralExpression.ofLong(Long.parseLong(lex), t.location());
            } catch (NumberFormatException e) {
                throw error(t, "long number too large: " + lex);
            }
        }
        if (match(TokenType.FLOAT_LITERAL)) {
            Token t = previous();
            String lex = t.lexeme();
            if (lex.endsWith("f") || lex.endsWith("F") || lex.endsWith("d") || lex.endsWith("D")) {
                lex = lex.substring(0, lex.length() - 1);
            }
            return LiteralExpression.ofFloat(Float.parseFloat(lex), t.location());
        }
        if (match(TokenType.DOUBLE_LITERAL)) {
            Token t = previous();
            String lex = t.lexeme();
            if (lex.endsWith("d") || lex.endsWith("D") || lex.endsWith("f") || lex.endsWith("F")) {
                lex = lex.substring(0, lex.length() - 1);
            }
            return LiteralExpression.ofDouble(Double.parseDouble(lex), t.location());
        }
        if (match(TokenType.STRING_LITERAL)) {
            Token t = previous();
            return LiteralExpression.ofString(t.lexeme(), t.location());
        }
        if (match(TokenType.NEW)) {
            return parseNew(previous());
        }
        if (isCastAhead()) {
            Token lparen = advance();
            TypeName type = parseType();
            consume(TokenType.RPAREN, "expected ')' after cast type");
            Expression operand = parseUnary();
            return new CastExpression(type, operand, lparen.location());
        }
        if (check(TokenType.IDENTIFIER) && isLambdaAhead()) {
            return parseLambda();
        }
        if (check(TokenType.LPAREN) && isLambdaAhead()) {
            return parseLambda();
        }
        if (match(TokenType.LPAREN)) {
            Token lparen = previous();
            Expression expr = parseExpression();
            consume(TokenType.RPAREN, "expected ')' after expression");
            return new GroupingExpression(expr, lparen.location());
        }
        if (match(TokenType.IDENTIFIER)) {
            Token id = previous();
            if (id.lexeme().equals("System") && match(TokenType.DOT)) {
                return parseSystemCall(id);
            }
            Expression factory = tryParseCollectionFactory(id);
            if (factory != null) {
                return factory;
            }
            if (match(TokenType.LPAREN)) {
                List<Expression> args = parseArgumentList();
                consume(TokenType.RPAREN, "expected ')' after arguments");
                return new MethodCallExpression(null, id.lexeme(), args, id.location());
            }
            return new VariableExpression(id.lexeme(), id.location());
        }
        throw error(peek(), "expected expression");
    }

    /**
     * One-dimensional array initializer {@code { e1, e2, ... }} (trailing comma allowed).
     * Multi-dimensional arrays do not exist in the language yet, so nested braces are rejected.
     */
    private Expression parseArrayInitializer(TypeName elementType) {
        Token open = consume(TokenType.LBRACE, "expected '{' to start array initializer");
        List<Expression> elements = new ArrayList<>();
        if (!check(TokenType.RBRACE)) {
            do {
                if (check(TokenType.RBRACE)) {
                    break; // trailing comma
                }
                if (check(TokenType.LBRACE)) {
                    if (elementType.kind() != TypeName.Kind.ARRAY) {
                        throw error(peek(), "nested array initializers are not supported for a non-array element type");
                    }
                    elements.add(parseArrayInitializer(elementType.typeArgument()));
                } else {
                    elements.add(parseExpression());
                }
            } while (match(TokenType.COMMA));
        }
        consume(TokenType.RBRACE, "expected '}' after array initializer");
        return new ArrayInitExpression(elementType, List.copyOf(elements), open.location());
    }

    private Expression parseNew(Token newTok) {
        skipPackageQualifier();
        boolean diamond = false;
        TypeName type;
        if (isPrimitiveTypeToken(peek())) {
            type = parseType();
        } else if (check(TokenType.IDENTIFIER)) {
            // Allow FQN: new com.demo.Box() and diamond: new Box<>()
            Token id = advance();
            StringBuilder qn = new StringBuilder(id.lexeme());
            while (check(TokenType.DOT)
                    && peekAt(1) != null
                    && peekAt(1).type() == TokenType.IDENTIFIER) {
                advance();
                Token next = consume(TokenType.IDENTIFIER, "expected identifier after '.'");
                qn.append('.').append(next.lexeme());
            }
            List<TypeName> typeArgs = List.of();
            if (match(TokenType.LT)) {
                if (check(TokenType.GT)) {
                    consume(TokenType.GT, "expected '>' after '<'");
                    diamond = true;
                } else {
                    typeArgs = parseTypeArgumentList();
                    consume(TokenType.GT, "expected '>' after type arguments");
                }
            }
            type = TypeName.ofClass(qn.toString(), typeArgs, id.location());
        } else {
            type = parseType();
        }
        if (match(TokenType.LBRACKET)) {
            if (type.kind() == TypeName.Kind.VOID) {
                throw error(newTok, "cannot create an array of 'void'");
            }
            if (match(TokenType.RBRACKET)) {
                // new T[] { a, b, c }   /   new T[][] { {..}, {..} }
                int extraDims = consumeArrayDims();
                type = wrapArrayDims(type, extraDims);
                if (!check(TokenType.LBRACE)) {
                    throw error(peek(), "array dimension missing: expected an array length or '{' initializer");
                }
                return parseArrayInitializer(type);
            }
            Expression length = parseExpression();
            consume(TokenType.RBRACKET, "expected ']' after array length");
            // new T[a][b][]: further sized dimensions, then optional unsized ones
            List<Expression> innerLengths = new ArrayList<>();
            int unsized = 0;
            while (check(TokenType.LBRACKET)) {
                if (peekAt(1) != null && peekAt(1).type() == TokenType.RBRACKET) {
                    unsized = consumeArrayDims();
                    break;
                }
                advance();
                innerLengths.add(parseExpression());
                consume(TokenType.RBRACKET, "expected ']' after array length");
            }
            TypeName elementType = wrapArrayDims(type, innerLengths.size() + unsized);
            return new NewArrayExpression(elementType, length, List.copyOf(innerLengths), newTok.location());
        }
        if (type.kind() != TypeName.Kind.CLASS) {
            throw error(newTok, "expected '[' after primitive type in 'new'");
        }
        consume(TokenType.LPAREN, "expected '(' after class name");
        List<Expression> args = parseArgumentList();
        consume(TokenType.RPAREN, "expected ')' after arguments");
        if (check(TokenType.LBRACE)) {
            return parseAnonymousClass(newTok, type, args);
        }
        List<TypeName> typeArgs = type.hasTypeArguments() ? type.typeArguments() : List.of();
        String className = type.name();
        String linkedBase = linkedHashAlias(className);
        if (linkedBase != null) {
            if (className.startsWith("LinkedHash")) {
                // LinkedHashMap/LinkedHashSet are insertion-ordered HashMap/HashSet: pass ordered=true.
                List<Expression> withFlag = new ArrayList<>();
                withFlag.add(LiteralExpression.ofBoolean(true, newTok.location()));
                withFlag.addAll(args);
                args = withFlag;
            }
            className = linkedBase;
        }
        return new NewObjectExpression(className, typeArgs, diamond, args, newTok.location());
    }

    /**
     * {@code new T() { members }}: hoisted into a nested class {@code Outer.Anon_N} that extends or implements
     * {@code T}. Captured locals (identifiers in the body that name a visible local/parameter) become
     * fields of the same name, initialised by a synthesized constructor, so the body needs no rewriting.
     * Not supported: constructor arguments for the base class, access to enclosing-instance members.
     */
    private Expression parseAnonymousClass(Token newTok, TypeName base, List<Expression> ctorArgs) {
        if (!ctorArgs.isEmpty()) {
            throw error(newTok, "anonymous class constructor arguments are not supported");
        }
        Token open = consume(TokenType.LBRACE, "expected '{'");
        int bodyStart = current;
        Map<String, LocalInfo> visible = new HashMap<>();
        // iterate outermost -> innermost so inner declarations win
        java.util.Iterator<Map<String, LocalInfo>> it = localScopes.descendingIterator();
        while (it.hasNext()) {
            visible.putAll(it.next());
        }
        String simple = "Anon_" + (++anonymousCounter);
        String full = typeNesting.isEmpty() ? simple : String.join(".", typeNesting) + "." + simple;
        List<FieldDeclaration> fields = new ArrayList<>();
        List<MethodDeclaration> methods = new ArrayList<>();
        String savedFull = currentTypeFullName;
        currentTypeFullName = full;
        typeNesting.addLast(simple);
        try {
            while (!check(TokenType.RBRACE) && !isAtEnd()) {
                parseClassMember(simple, fields, methods, false, false, false, EffectContract.empty(open.location()));
            }
        } finally {
            typeNesting.removeLast();
            currentTypeFullName = savedFull;
        }
        int bodyEnd = current;
        consume(TokenType.RBRACE, "expected '}' after anonymous class body");

        if (BUILTIN_SAM_CLASSES.contains(base.name())) {
            // Built-in closure types (Runnable, ...) are not subclassable: a single-method body is a lambda.
            if (!fields.isEmpty() || methods.size() != 1 || methods.get(0).isStatic()
                    || methods.get(0).isConstructor() || methods.get(0).body() == null) {
                throw error(newTok, "an anonymous " + base.name()
                        + " must declare exactly one method and no fields (or use a lambda)");
            }
            MethodDeclaration only = methods.get(0);
            List<LambdaParameter> lps = new ArrayList<>();
            for (Parameter prm : only.parameters()) {
                lps.add(new LambdaParameter(prm.type(), prm.isArray(), prm.name(), prm.location()));
            }
            return new LambdaExpression(lps, null, only.body(), newTok.location());
        }

        java.util.Set<String> declared = new HashSet<>();
        for (FieldDeclaration f : fields) {
            declared.add(f.name());
        }
        java.util.LinkedHashSet<String> captured = new java.util.LinkedHashSet<>();
        for (int i = bodyStart; i < bodyEnd; i++) {
            Token t = tokens.get(i);
            if (t.type() != TokenType.IDENTIFIER || !visible.containsKey(t.lexeme()) || declared.contains(t.lexeme())) {
                continue;
            }
            Token prev = i > 0 ? tokens.get(i - 1) : null;
            Token next = i + 1 < tokens.size() ? tokens.get(i + 1) : null;
            if (prev != null && prev.type() == TokenType.DOT) {
                continue;
            }
            if (next != null && next.type() == TokenType.LPAREN) {
                continue;
            }
            captured.add(t.lexeme());
        }

        SourceLocation loc = newTok.location();
        List<Parameter> ctorParams = new ArrayList<>();
        List<Statement> ctorBody = new ArrayList<>();
        List<Expression> creationArgs = new ArrayList<>();
        List<FieldDeclaration> allFields = new ArrayList<>();
        for (String name : captured) {
            LocalInfo info = visible.get(name);
            if (info.type() == null) {
                throw error(newTok, "anonymous class cannot capture local variable '" + name
                        + "' whose type is not written explicitly; declare its type");
            }
            allFields.add(new FieldDeclaration(info.type(), info.isArray(), name, Visibility.PRIVATE,
                    false, false, null, loc));
            ctorParams.add(new Parameter(info.type(), name, info.isArray(), loc));
            ctorBody.add(new ExpressionStatement(new AssignmentExpression(
                    new FieldAccessExpression(new ThisExpression(loc), name, loc),
                    new VariableExpression(name, loc), loc), loc));
            creationArgs.add(new VariableExpression(name, loc));
        }
        for (FieldDeclaration f : fields) {
            if (!captured.isEmpty() && f.initializer() != null && !f.isStatic()
                    && !(f.initializer() instanceof ArrayInitExpression)) {
                // Field initializers may read captured values: run them after the captures are stored.
                ctorBody.add(new ExpressionStatement(new AssignmentExpression(
                        new FieldAccessExpression(new ThisExpression(loc), f.name(), loc),
                        f.initializer(), loc), loc));
                allFields.add(new FieldDeclaration(f.type(), f.isArray(), f.name(), f.visibility(), false,
                        f.isVolatile(), null, f.location()));
            } else {
                allFields.add(f);
            }
        }
        if (!captured.isEmpty()) {
            methods.add(new MethodDeclaration(
                    true, false, false, true, false, false, false, List.of(),
                    TypeName.ofVoid(loc), false, simple, ctorParams, new BlockStatement(ctorBody, loc),
                    null, EffectContract.empty(loc), Visibility.PUBLIC, loc));
        }
        String pkg = currentPackage == null ? "" : currentPackage;
        nestedTypes.add(new ClassDeclaration(
                full, pkg, List.copyOf(currentImports), Visibility.PACKAGE, List.of(), base.name(),
                base.hasTypeArguments() ? base.typeArguments() : List.of(), List.of(),
                allFields, methods, false, false, false, true, loc));
        return new NewObjectExpression(full, creationArgs, loc);
    }

    /**
     * Distinguishes a cast {@code (Type) expr} from a parenthesised expression {@code (expr)}.
     * A class-name cast is only recognised when the token after {@code )} can start an operand,
     * so {@code (p) + 1} stays an addition.
     */
    private boolean isCastAhead() {
        if (!check(TokenType.LPAREN)) {
            return false;
        }
        Token typeTok = peekAt(1);
        if (typeTok == null) {
            return false;
        }
        boolean primitive = isPrimitiveTypeToken(typeTok);
        boolean classType = typeTok.type() == TokenType.IDENTIFIER
                && knownClassNames.contains(typeTok.lexeme());
        if (!primitive && !classType) {
            return false;
        }
        int idx = 2;
        // NativePtr<Point> / NativePtr<Void>
        if (typeTok.type() == TokenType.IDENTIFIER
                && "NativePtr".equals(typeTok.lexeme())
                && peekAt(idx) != null
                && peekAt(idx).type() == TokenType.LT) {
            idx++; // <
            Token arg = peekAt(idx);
            if (arg == null || (arg.type() != TokenType.IDENTIFIER && !isPrimitiveTypeToken(arg))) {
                return false;
            }
            idx++;
            if (peekAt(idx) == null || peekAt(idx).type() != TokenType.GT) {
                return false;
            }
            idx++; // >
        } else if (classType && peekAt(idx) != null && peekAt(idx).type() == TokenType.LT) {
            // Parameterized cast target: (Comparable<?>) x, (List<String>) o, (Map<K, V>) m
            int depth = 0;
            while (true) {
                Token t = peekAt(idx);
                if (t == null) {
                    return false;
                }
                switch (t.type()) {
                    case LT -> depth++;
                    case GT -> depth--;
                    case IDENTIFIER, COMMA, QUESTION, EXTENDS, SUPER, DOT -> { }
                    default -> {
                        return false;
                    }
                }
                idx++;
                if (depth == 0) {
                    break;
                }
            }
        }
        Token rparen = peekAt(idx);
        if (rparen == null || rparen.type() != TokenType.RPAREN) {
            return false;
        }
        Token after = peekAt(idx + 1);
        if (after == null) {
            return false;
        }
        return switch (after.type()) {
            case IDENTIFIER, INT_LITERAL, LONG_LITERAL, CHAR_LITERAL, FLOAT_LITERAL, DOUBLE_LITERAL,
                 STRING_LITERAL, TRUE, FALSE, NULL, THIS, SUPER, NEW, LPAREN, NOT, BIT_NOT -> true;
            case MINUS, PLUS -> primitive;
            default -> false;
        };
    }

    /**
     * Lookahead for {@code id -> ...} or {@code (a, b) -> ...} without consuming tokens.
     */
    private boolean isLambdaAhead() {
        if (inCaseLabel) {
            return false;
        }
        int saved = current;
        try {
            if (check(TokenType.IDENTIFIER)) {
                advance();
                return check(TokenType.ARROW);
            }
            if (check(TokenType.LPAREN)) {
                advance();
                if (check(TokenType.RPAREN)) {
                    advance();
                    return check(TokenType.ARROW);
                }
                if (!check(TokenType.IDENTIFIER)) {
                    return false;
                }
                do {
                    advance(); // param name
                    if (check(TokenType.IDENTIFIER) && knownClassNames.contains(peek().lexeme())) {
                        // typed param: Type name
                        advance();
                        if (match(TokenType.LBRACKET)) {
                            consume(TokenType.RBRACKET, "expected ']'");
                        }
                    } else if (isPrimitiveTypeToken(peek())) {
                        advance();
                        if (match(TokenType.LBRACKET)) {
                            consume(TokenType.RBRACKET, "expected ']'");
                        }
                    }
                } while (match(TokenType.COMMA));
                if (!check(TokenType.RPAREN)) {
                    return false;
                }
                advance();
                return check(TokenType.ARROW);
            }
            return false;
        } finally {
            current = saved;
        }
    }

    private LambdaExpression parseLambda() {
        Token start = peek();
        List<LambdaParameter> params = new ArrayList<>();
        if (check(TokenType.IDENTIFIER)) {
            Token id = advance();
            params.add(new LambdaParameter(id.lexeme(), id.location()));
        } else {
            consume(TokenType.LPAREN, "expected '(' in lambda");
            if (!check(TokenType.RPAREN)) {
                do {
                    TypeName type = null;
                    boolean isArray = false;
                    if (isPrimitiveTypeToken(peek())
                            || (check(TokenType.IDENTIFIER) && !checkNext(TokenType.COMMA)
                            && !checkNext(TokenType.RPAREN))) {
                        if (check(TokenType.IDENTIFIER) && !isPrimitiveTypeToken(peek())
                                && !knownClassNames.contains(peek().lexeme())
                                && checkNext(TokenType.IDENTIFIER)) {
                            // single identifier param without type — handled below
                        } else if (isPrimitiveTypeToken(peek())
                                || (check(TokenType.IDENTIFIER) && knownClassNames.contains(peek().lexeme()))) {
                            type = parseType();
                            int lambdaDims = consumeArrayDims();
                            if (lambdaDims > 0) {
                                isArray = true;
                                type = wrapArrayDims(type, lambdaDims - 1);
                            }
                        }
                    }
                    Token name = consume(TokenType.IDENTIFIER, "expected lambda parameter name");
                    if (!isArray) {
                        int lambdaDims2 = consumeArrayDims();
                        if (lambdaDims2 > 0 && type != null) {
                            isArray = true;
                            type = wrapArrayDims(type, lambdaDims2 - 1);
                        }
                    }
                    params.add(new LambdaParameter(type, isArray, name.lexeme(), name.location()));
                } while (match(TokenType.COMMA));
            }
            consume(TokenType.RPAREN, "expected ')' after lambda parameters");
        }
        consume(TokenType.ARROW, "expected '->' in lambda");
        Expression expressionBody = null;
        BlockStatement blockBody = null;
        pushLocalScope();
        try {
            for (LambdaParameter lp : params) {
                declareLocal(lp.name(), lp.type(), lp.isArray());
            }
            if (check(TokenType.LBRACE)) {
                blockBody = parseBlock();
            } else {
                expressionBody = parseExpression();
            }
        } finally {
            popLocalScope();
        }
        return new LambdaExpression(params, expressionBody, blockBody, start.location());
    }

    private List<Expression> parseArgumentList() {
        List<Expression> args = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            do {
                args.add(parseExpression());
            } while (match(TokenType.COMMA));
        }
        return args;
    }

    /**
     * Lookahead for {@code <TypeArgs>(} after {@code .ident}, so {@code a.b < c} stays a comparison.
     */
    private boolean isMethodTypeArgsAhead() {
        if (!check(TokenType.LT)) {
            return false;
        }
        int i = 1;
        int depth = 1;
        while (depth > 0) {
            Token t = peekAt(i);
            if (t == null || t.type() == TokenType.EOF || t.type() == TokenType.SEMICOLON) {
                return false;
            }
            if (t.type() == TokenType.LT) {
                depth++;
            } else if (t.type() == TokenType.GT) {
                depth--;
            }
            i++;
            if (i > 64) {
                return false;
            }
        }
        Token after = peekAt(i);
        return after != null && after.type() == TokenType.LPAREN;
    }

    private List<TypeName> parseMethodTypeArguments() {
        consume(TokenType.LT, "expected '<'");
        List<TypeName> args = new ArrayList<>();
        do {
            args.add(parseType());
        } while (match(TokenType.COMMA));
        consume(TokenType.GT, "expected '>' after type arguments");
        return args;
    }

    /**
     * {@code System.*}. {@code System.out.println} keeps its dedicated AST node (fast path);
     * everything else is desugared into calls on the stdlib class {@code JnSystem}
     * ({@code SystemLib.java}), so the compiler core needs no new intrinsics:
     * <ul>
     *   <li>{@code System.out.print(x)} → {@code JnSystem.print("" + x)}</li>
     *   <li>{@code System.err.print/println(x)} → {@code JnSystem.errPrint/errPrintln("" + x)}</li>
     *   <li>{@code System.exit / currentTimeMillis / nanoTime / arraycopy / lineSeparator}
     *       → the same-named {@code JnSystem} static method</li>
     * </ul>
     */
    private Expression parseSystemCall(Token systemTok) {
        Token next = consume(TokenType.IDENTIFIER, "expected member after System.");
        String member = next.lexeme();
        if (member.equals("gc")) {
            consume(TokenType.LPAREN, "expected '(' after System.gc");
            consume(TokenType.RPAREN, "expected ')' after System.gc");
            return new GcCollectExpression(systemTok.location());
        }
        if (member.equals("exit") || member.equals("currentTimeMillis") || member.equals("nanoTime")
                || member.equals("arraycopy") || member.equals("lineSeparator")) {
            consume(TokenType.LPAREN, "expected '(' after System." + member);
            List<Expression> args = parseArgumentList();
            consume(TokenType.RPAREN, "expected ')' after arguments");
            return systemHelperCall(member, args, systemTok);
        }
        if (!member.equals("out") && !member.equals("err")) {
            throw error(next, "unsupported System member '" + member
                    + "' (supported: out, err, exit, gc, currentTimeMillis, nanoTime, arraycopy, lineSeparator)");
        }
        boolean err = member.equals("err");
        consume(TokenType.DOT, "expected '.' after System." + member);
        Token method = consume(TokenType.IDENTIFIER, "expected 'println' or 'print'");
        boolean newline;
        boolean formatted = false;
        if (method.lexeme().equals("println")) {
            newline = true;
        } else if (method.lexeme().equals("print")) {
            newline = false;
        } else if (method.lexeme().equals("printf") || method.lexeme().equals("format")) {
            newline = false;
            formatted = true;
        } else {
            throw error(method, "only System." + member + ".println / print / printf are supported");
        }
        consume(TokenType.LPAREN, "expected '(' after " + method.lexeme());
        Expression arg = null;
        if (formatted) {
            List<Expression> fargs = parseArgumentList();
            consume(TokenType.RPAREN, "expected ')' after " + method.lexeme() + " arguments");
            if (fargs.isEmpty()) {
                throw error(method, "System." + member + "." + method.lexeme() + " requires a format string");
            }
            arg = new MethodCallExpression(
                    new VariableExpression("JnFormat", systemTok.location()),
                    "format", fargs, systemTok.location());
        } else {
            if (!check(TokenType.RPAREN)) {
                arg = parseExpression();
            }
            consume(TokenType.RPAREN, "expected ')' after " + method.lexeme() + " argument");
        }
        if (arg == null) {
            if (!newline) {
                throw error(method, "System." + member + ".print requires an argument");
            }
            arg = LiteralExpression.ofString("", method.location());
        }
        if (!err && newline) {
            return new PrintlnExpression(arg, systemTok.location());
        }
        Expression asText = new BinaryExpression(
                LiteralExpression.ofString("", systemTok.location()),
                BinaryOp.ADD,
                new GroupingExpression(arg, systemTok.location()),
                systemTok.location());
        String helper = err ? (newline ? "errPrintln" : "errPrint") : "print";
        return systemHelperCall(helper, List.of(asText), systemTok);
    }

    /**
     * Collection factories ({@code List.of}, {@code Set.of}, {@code Map.of}, {@code Map.entry},
     * {@code List.copyOf}, multi-argument {@code Arrays.asList}) are desugared to fixed-arity static
     * helpers in the stdlib ({@code JnLists/JnSets/JnMaps}); the language has no varargs or static
     * interface methods yet. Returns null (nothing consumed) when the tokens are not such a call.
     */
    private Expression tryParseCollectionFactory(Token owner) {
        Token dot = peek();
        Token member = peekAt(1);
        Token paren = peekAt(2);
        if (dot == null || dot.type() != TokenType.DOT || member == null
                || member.type() != TokenType.IDENTIFIER || paren == null
                || paren.type() != TokenType.LPAREN) {
            return null;
        }
        String o = owner.lexeme();
        String m = member.lexeme();
        String helper = null;
        if (o.equals("List") && (m.equals("of") || m.equals("copyOf"))) {
            helper = "JnLists";
        } else if (o.equals("Set") && (m.equals("of") || m.equals("copyOf"))) {
            helper = "JnSets";
        } else if (o.equals("Map") && (m.equals("of") || m.equals("entry") || m.equals("copyOf"))) {
            helper = "JnMaps";
        } else if (o.equals("Arrays") && m.equals("asList")) {
            helper = "Arrays";
        }
        if (helper == null) {
            return null;
        }
        advance();
        advance();
        advance();
        List<Expression> args = parseArgumentList();
        consume(TokenType.RPAREN, "expected ')' after arguments");
        String name = m;
        if (helper.equals("Arrays")) {
            if (args.size() == 1) {
                name = "asList";
            } else {
                helper = "JnLists";
                name = "of";
            }
        }
        return new MethodCallExpression(
                new VariableExpression(helper, owner.location()), name, args, owner.location());
    }

    private static Expression systemHelperCall(String name, List<Expression> args, Token systemTok) {
        return new MethodCallExpression(
                new VariableExpression("JnSystem", systemTok.location()),
                name, args, systemTok.location());
    }
    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) {
            return advance();
        }
        throw error(peek(), message);
    }

    private boolean check(TokenType type) {
        if (isAtEnd()) {
            return type == TokenType.EOF;
        }
        return peek().type() == type;
    }

    private boolean checkNext(TokenType type) {
        Token next = peekAt(1);
        return next != null && next.type() == type;
    }

    private Token advance() {
        if (!isAtEnd()) {
            current++;
        }
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type() == TokenType.EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token peekAt(int offset) {
        int index = current + offset;
        if (index >= tokens.size()) {
            return null;
        }
        return tokens.get(index);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    /** Allow {@code .await(...)} on {@code Condition} despite {@code await} being a keyword. */
    private Token consumeMemberNameAfterDot() {
        if (check(TokenType.IDENTIFIER)) {
            return advance();
        }
        if (match(TokenType.AWAIT)) {
            return previous();
        }
        throw error(peek(), "expected member name after '.'");
    }

    private ParseException error(Token token, String message) {
        return new ParseException(token.location(), message);
    }

    private void synchronize() {
        advance();
        while (!isAtEnd()) {
            if (previous().type() == TokenType.SEMICOLON) {
                return;
            }
            switch (peek().type()) {
                case CLASS, PUBLIC, PRIVATE, PROTECTED, STATIC, IF, WHILE, FOR, RETURN,
                     BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BOOLEAN, CHAR, VOID -> {
                    return;
                }
                default -> advance();
            }
        }
    }
}
