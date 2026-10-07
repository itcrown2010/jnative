package io.jnative.parser;

import io.jnative.ast.*;
import io.jnative.diagnostics.DiagnosticReporter;
import io.jnative.lexer.Token;
import io.jnative.lexer.TokenType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Recursive-descent parser for the JNative Java subset (0.8 native data model).
 */
public final class Parser {
    private final List<Token> tokens;
    private final DiagnosticReporter reporter;
    private int current;
    private final Set<String> knownClassNames = new HashSet<>();
    private final Map<String, List<MarkerAnnotation>> typeAnnotations = new LinkedHashMap<>();
    private final Map<String, List<MarkerAnnotation>> methodAnnotations = new LinkedHashMap<>();
    private final Map<String, List<MarkerAnnotation>> fieldAnnotations = new LinkedHashMap<>();

    public Parser(List<Token> tokens, DiagnosticReporter reporter) {
        this.tokens = tokens;
        this.reporter = reporter;
    }

    public CompilationUnit parse(String fileName) {
        // Pre-scan class names so local variable declarations like `Person p` work.
        collectClassNames();
        current = 0;
        typeAnnotations.clear();
        methodAnnotations.clear();
        fieldAnnotations.clear();

        List<ClassDeclaration> classes = new ArrayList<>();
        var loc = peek().location();
        try {
            while (!isAtEnd()) {
                if (match(TokenType.PACKAGE) || match(TokenType.IMPORT)) {
                    // 0.17: accept package/import for source compatibility; flatten into one CU.
                    skipUntilSemicolon();
                    continue;
                }
                classes.add(parseTypeDeclaration());
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
            if ((match(TokenType.CLASS) || match(TokenType.INTERFACE)) && check(TokenType.IDENTIFIER)) {
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
            } else {
                reporter.error(ann.location(),
                        "@" + ann.name() + " is not allowed on type declarations");
            }
        }
        match(TokenType.PUBLIC);
        boolean isAbstract = match(TokenType.ABSTRACT);
        boolean isInterface = false;
        if (match(TokenType.INTERFACE)) {
            isInterface = true;
            if (nativeStruct) {
                reporter.error(previous().location(), "@NativeStruct cannot be an interface");
            }
        } else {
            consume(TokenType.CLASS, "expected 'class' or 'interface'");
        }
        Token name = consume(TokenType.IDENTIFIER, "expected type name");
        if (!pendingTypeAnns.isEmpty()) {
            typeAnnotations.put(name.lexeme(), List.copyOf(pendingTypeAnns));
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
        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            parseClassMember(name.lexeme(), fields, methods, nativeStruct, isInterface, isAbstract, typeEffects);
        }
        consume(TokenType.RBRACE, "expected '}'");
        return new ClassDeclaration(
                name.lexeme(), typeParams, superClassName, superClassTypeArgs,
                implementedInterfaces, fields, methods,
                nativeStruct, isInterface, isAbstract, name.location());
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

    /** Class or interface reference with optional type arguments. */
    private TypeName parseClassTypeRef(String message) {
        Token id = consume(TokenType.IDENTIFIER, message);
        List<TypeName> args = List.of();
        if (match(TokenType.LT)) {
            if (!check(TokenType.GT)) {
                args = parseTypeArgumentList();
            }
            consume(TokenType.GT, "expected '>' after type arguments");
        }
        return TypeName.ofClass(id.lexeme(), args, id.location());
    }

    private void parseClassMember(String className,
                                  List<FieldDeclaration> fields,
                                  List<MethodDeclaration> methods,
                                  boolean nativeStruct,
                                  boolean isInterface,
                                  boolean classIsAbstract,
                                  EffectContract typeEffects) {
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
            } else {
                reporter.error(ann.location(),
                        "@" + ann.name() + " is not allowed on class members");
            }
        }
        match(TokenType.PUBLIC);
        match(TokenType.PRIVATE);
        match(TokenType.PROTECTED);
        boolean isStatic = false;
        boolean isNative = false;
        boolean isVolatile = false;
        boolean isAbstract = false;
        boolean isAsync = false;
        boolean isDefault = false;
        for (int i = 0; i < 9; i++) {
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
            } else {
                break;
            }
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
            BlockStatement body = parseBlock();
            methods.add(new MethodDeclaration(
                    true, false, false, true, false, false, List.of(), TypeName.ofVoid(name.location()), false,
                    className, params, body, null, effectContract, name.location()));
            return;
        }

        TypeName type = parseType();
        boolean isArray = false;
        if (match(TokenType.LBRACKET)) {
            consume(TokenType.RBRACKET, "expected ']'");
            isArray = true;
        }
        Token name = consume(TokenType.IDENTIFIER, "expected member name");
        if (!isArray && match(TokenType.LBRACKET)) {
            consume(TokenType.RBRACKET, "expected ']'");
            isArray = true;
        }

        if (check(TokenType.LPAREN)) {
            if (nativeStruct) {
                reporter.error(name.location(),
                        "@NativeStruct cannot declare methods");
            }
            List<Parameter> params = parseParameterList();
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
                    true, isStatic, isNative, false, methodAbstract && !isNative, isAsync,
                    methodTypeParams, type, isArray,
                    name.lexeme(), params, body, nativeAnnotation, effectContract, name.location()));
            if (!pendingMemberAnns.isEmpty()) {
                methodAnnotations.put(className + "." + name.lexeme(), List.copyOf(pendingMemberAnns));
            }
            return;
        }

        // field
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
            initializer = parseExpression();
        }
        consume(TokenType.SEMICOLON, "expected ';' after field declaration");
        fields.add(new FieldDeclaration(
                type, isArray, name.lexeme(), isStatic, isVolatile, initializer, name.location()));
        if (!pendingMemberAnns.isEmpty()) {
            fieldAnnotations.put(className + "." + name.lexeme(), List.copyOf(pendingMemberAnns));
        }
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

    private ParsedAnnotation parseAnnotation() {
        Token at = consume(TokenType.AT, "expected '@'");
        Token name = consume(TokenType.IDENTIFIER, "expected annotation name");
        String annName = name.lexeme();
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
        return params;
    }

    private Parameter parseParameter() {
        TypeName type = parseType();
        boolean isArray = false;
        if (match(TokenType.LBRACKET)) {
            consume(TokenType.RBRACKET, "expected ']'");
            isArray = true;
        }
        Token name = consume(TokenType.IDENTIFIER, "expected parameter name");
        if (!isArray && match(TokenType.LBRACKET)) {
            consume(TokenType.RBRACKET, "expected ']'");
            isArray = true;
        }
        return new Parameter(type, name.lexeme(), isArray, name.location());
    }

    private TypeName parseType() {
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
            if (id.lexeme().equals("NativePtr")) {
                TypeName arg = null;
                if (match(TokenType.LT)) {
                    arg = parseNativePtrTypeArgument();
                    consume(TokenType.GT, "expected '>' after NativePtr type argument");
                }
                return TypeName.ofNativePtr(arg, id.location());
            }
            List<TypeName> args = List.of();
            if (match(TokenType.LT)) {
                if (!check(TokenType.GT)) {
                    args = parseTypeArgumentList();
                }
                consume(TokenType.GT, "expected '>' after type arguments");
            }
            return TypeName.ofClass(id.lexeme(), args, id.location());
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
        return parseType();
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

    private BlockStatement parseBlock() {
        Token lbrace = consume(TokenType.LBRACE, "expected '{'");
        List<Statement> statements = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            statements.add(parseStatement());
        }
        consume(TokenType.RBRACE, "expected '}'");
        return new BlockStatement(statements, lbrace.location());
    }

    private Statement parseStatement() {
        try {
            if (match(TokenType.IF)) {
                return parseIf();
            }
            if (match(TokenType.WHILE)) {
                return parseWhile();
            }
            if (match(TokenType.FOR)) {
                return parseFor();
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
        BlockStatement tryBlock = parseBlock();
        List<CatchClause> catches = new ArrayList<>();
        while (match(TokenType.CATCH)) {
            consume(TokenType.LPAREN, "expected '(' after 'catch'");
            TypeName catchType = parseType();
            Token varName = consume(TokenType.IDENTIFIER, "expected catch variable name");
            consume(TokenType.RPAREN, "expected ')' after catch parameter");
            BlockStatement catchBody = parseBlock();
            catches.add(new CatchClause(catchType, varName.lexeme(), catchBody, start.location()));
        }
        BlockStatement finallyBlock = null;
        if (match(TokenType.FINALLY)) {
            finallyBlock = parseBlock();
        }
        if (catches.isEmpty() && finallyBlock == null) {
            throw new ParseException(start.location(), "try must have at least one catch or finally block");
        }
        return new TryStatement(tryBlock, catches, finallyBlock, start.location());
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
        if (check(TokenType.AT)
                && peekAt(1) != null
                && peekAt(1).type() == TokenType.IDENTIFIER
                && "Stack".equals(peekAt(1).lexeme())) {
            return true;
        }
        if (isPrimitiveTypeToken(peek())) {
            return true;
        }
        if (check(TokenType.IDENTIFIER) && "NativePtr".equals(peek().lexeme())) {
            return isNativePtrVariableDeclAhead();
        }
        if (check(TokenType.IDENTIFIER) && knownClassNames.contains(peek().lexeme())) {
            int afterType = 1;
            int afterArgs = indexAfterTypeArguments(afterType);
            if (afterArgs >= 0) {
                afterType = afterArgs;
            }
            Token next = peekAt(afterType);
            if (next != null && next.type() == TokenType.IDENTIFIER) {
                return true;
            }
            if (next != null && next.type() == TokenType.LBRACKET) {
                Token close = peekAt(afterType + 1);
                Token name = peekAt(afterType + 2);
                return close != null && close.type() == TokenType.RBRACKET
                        && name != null && name.type() == TokenType.IDENTIFIER;
            }
        }
        return false;
    }

    /** {@code NativePtr p} / {@code NativePtr<T> p} / optional {@code []}. */
    private boolean isNativePtrVariableDeclAhead() {
        int i = 1;
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
                if (i > current + 64) {
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
            Token close = peekAt(i + 1);
            Token name = peekAt(i + 2);
            return close != null && close.type() == TokenType.RBRACKET
                    && name != null && name.type() == TokenType.IDENTIFIER;
        }
        return false;
    }

    private boolean isPrimitiveTypeToken(Token token) {
        return switch (token.type()) {
            case BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BOOLEAN, CHAR -> true;
            default -> false;
        };
    }

    private VariableDeclaration parseVariableDeclaration() {
        boolean stackAllocated = false;
        if (check(TokenType.AT)) {
            ParsedAnnotation ann = parseAnnotation();
            if ("Stack".equals(ann.name())) {
                stackAllocated = true;
            } else {
                reporter.error(ann.location(),
                        "@" + ann.name() + " is not allowed on local variables");
            }
        }
        TypeName type = parseType();
        boolean isArray = false;
        if (match(TokenType.LBRACKET)) {
            consume(TokenType.RBRACKET, "expected ']'");
            isArray = true;
        }
        Token name = consume(TokenType.IDENTIFIER, "expected variable name");
        if (!isArray && match(TokenType.LBRACKET)) {
            consume(TokenType.RBRACKET, "expected ']'");
            isArray = true;
        }
        Expression init = null;
        if (match(TokenType.ASSIGN)) {
            init = parseExpression();
        }
        consume(TokenType.SEMICOLON, "expected ';' after variable declaration");
        return new VariableDeclaration(
                type, isArray, name.lexeme(), init, stackAllocated, name.location());
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

    private ForStatement parseFor() {
        Token forTok = previous();
        consume(TokenType.LPAREN, "expected '(' after for");

        Statement initializer;
        if (match(TokenType.SEMICOLON)) {
            initializer = null;
        } else if (isVariableDeclarationStart()) {
            initializer = parseVariableDeclaration();
        } else {
            Expression expr = parseExpression();
            consume(TokenType.SEMICOLON, "expected ';' after for initializer");
            initializer = new ExpressionStatement(expr, expr.location());
        }

        Expression condition = null;
        if (!check(TokenType.SEMICOLON)) {
            condition = parseExpression();
        }
        consume(TokenType.SEMICOLON, "expected ';' after for condition");

        Expression update = null;
        if (!check(TokenType.RPAREN)) {
            update = parseExpression();
        }
        consume(TokenType.RPAREN, "expected ')' after for clauses");
        Statement body = parseStatement();
        return new ForStatement(initializer, condition, update, body, forTok.location());
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

    private ExpressionStatement parseExpressionStatement() {
        Expression expr = parseExpression();
        consume(TokenType.SEMICOLON, "expected ';' after expression");
        return new ExpressionStatement(expr, expr.location());
    }

    private Expression parseExpression() {
        return parseAssignment();
    }

    private Expression parseAssignment() {
        Expression expr = parseOr();
        if (match(TokenType.ASSIGN)) {
            Token eq = previous();
            Expression value = parseAssignment();
            if (isAssignable(expr)) {
                return new AssignmentExpression(expr, value, eq.location());
            }
            throw error(eq, "invalid assignment target");
        }
        return expr;
    }

    private boolean isAssignable(Expression expr) {
        return expr instanceof VariableExpression
                || expr instanceof FieldAccessExpression
                || expr instanceof ArrayAccessExpression;
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
        Expression expr = parseEquality();
        while (match(TokenType.AND)) {
            Token op = previous();
            Expression right = parseEquality();
            expr = new BinaryExpression(expr, BinaryOp.AND, right, op.location());
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
        Expression expr = parseAdditive();
        while (true) {
            if (match(TokenType.INSTANCEOF)) {
                Token op = previous();
                TypeName type = parseType();
                expr = new InstanceOfExpression(expr, type, op.location());
                continue;
            }
            if (match(TokenType.LT, TokenType.LE, TokenType.GT, TokenType.GE)) {
                Token op = previous();
                Expression right = parseAdditive();
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

    private Expression parseUnary() {
        if (match(TokenType.NOT)) {
            Token op = previous();
            return new UnaryExpression(UnaryOp.NOT, parseUnary(), op.location());
        }
        if (match(TokenType.MINUS)) {
            Token op = previous();
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
                Token op = previous();
                if (!isAssignable(expr)) {
                    throw error(op, "invalid increment/decrement target");
                }
                BinaryOp bop = op.type() == TokenType.PLUS_PLUS ? BinaryOp.ADD : BinaryOp.SUB;
                Expression one = LiteralExpression.ofInt(1, op.location());
                Expression rhs = new BinaryExpression(expr, bop, one, op.location());
                expr = new AssignmentExpression(expr, rhs, op.location());
                continue;
            }
            if (match(TokenType.COLON_COLON)) {
                Token methodTok = consume(TokenType.IDENTIFIER, "expected method name after '::'");
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

    private Expression parsePrimary() {
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
        if (check(TokenType.SUPER)) {
            throw error(peek(), "'super' is only supported as a constructor delegation 'super(...)'");
        }
        if (match(TokenType.INT_LITERAL)) {
            Token t = previous();
            return LiteralExpression.ofInt(Integer.parseInt(t.lexeme()), t.location());
        }
        if (match(TokenType.LONG_LITERAL)) {
            Token t = previous();
            String lex = t.lexeme();
            if (lex.endsWith("L") || lex.endsWith("l")) {
                lex = lex.substring(0, lex.length() - 1);
            }
            return LiteralExpression.ofLong(Long.parseLong(lex), t.location());
        }
        if (match(TokenType.DOUBLE_LITERAL)) {
            Token t = previous();
            return LiteralExpression.ofDouble(Double.parseDouble(t.lexeme()), t.location());
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
            if (match(TokenType.LPAREN)) {
                List<Expression> args = parseArgumentList();
                consume(TokenType.RPAREN, "expected ')' after arguments");
                return new MethodCallExpression(null, id.lexeme(), args, id.location());
            }
            return new VariableExpression(id.lexeme(), id.location());
        }
        throw error(peek(), "expected expression");
    }

    private Expression parseNew(Token newTok) {
        boolean diamond = false;
        TypeName type;
        if (isPrimitiveTypeToken(peek())) {
            type = parseType();
        } else if (check(TokenType.IDENTIFIER)) {
            Token id = advance();
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
            type = TypeName.ofClass(id.lexeme(), typeArgs, id.location());
        } else {
            type = parseType();
        }
        if (match(TokenType.LBRACKET)) {
            Expression length = parseExpression();
            consume(TokenType.RBRACKET, "expected ']' after array length");
            if (type.kind() == TypeName.Kind.VOID) {
                throw error(newTok, "cannot create an array of 'void'");
            }
            return new NewArrayExpression(type, length, newTok.location());
        }
        if (type.kind() != TypeName.Kind.CLASS) {
            throw error(newTok, "expected '[' after primitive type in 'new'");
        }
        consume(TokenType.LPAREN, "expected '(' after class name");
        List<Expression> args = parseArgumentList();
        consume(TokenType.RPAREN, "expected ')' after arguments");
        List<TypeName> typeArgs = type.hasTypeArguments() ? type.typeArguments() : List.of();
        return new NewObjectExpression(type.name(), typeArgs, diamond, args, newTok.location());
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
            case IDENTIFIER, INT_LITERAL, LONG_LITERAL, DOUBLE_LITERAL, STRING_LITERAL,
                 TRUE, FALSE, NULL, THIS, SUPER, NEW, LPAREN, NOT -> true;
            case MINUS, PLUS -> primitive;
            default -> false;
        };
    }

    /**
     * Lookahead for {@code id -> ...} or {@code (a, b) -> ...} without consuming tokens.
     */
    private boolean isLambdaAhead() {
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
                            if (match(TokenType.LBRACKET)) {
                                consume(TokenType.RBRACKET, "expected ']'");
                                isArray = true;
                            }
                        }
                    }
                    Token name = consume(TokenType.IDENTIFIER, "expected lambda parameter name");
                    if (!isArray && match(TokenType.LBRACKET)) {
                        consume(TokenType.RBRACKET, "expected ']'");
                        isArray = true;
                    }
                    params.add(new LambdaParameter(type, isArray, name.lexeme(), name.location()));
                } while (match(TokenType.COMMA));
            }
            consume(TokenType.RPAREN, "expected ')' after lambda parameters");
        }
        consume(TokenType.ARROW, "expected '->' in lambda");
        Expression expressionBody = null;
        BlockStatement blockBody = null;
        if (check(TokenType.LBRACE)) {
            blockBody = parseBlock();
        } else {
            expressionBody = parseExpression();
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

    private Expression parseSystemCall(Token systemTok) {
        Token next = consume(TokenType.IDENTIFIER, "expected member after System.");
        if (next.lexeme().equals("gc")) {
            consume(TokenType.LPAREN, "expected '(' after System.gc");
            consume(TokenType.RPAREN, "expected ')' after System.gc");
            return new GcCollectExpression(systemTok.location());
        }
        if (!next.lexeme().equals("out")) {
            throw error(next, "expected 'out' or 'gc' after System.");
        }
        consume(TokenType.DOT, "expected '.' after System.out");
        Token println = consume(TokenType.IDENTIFIER, "expected 'println'");
        if (!println.lexeme().equals("println")) {
            throw error(println, "only System.out.println / System.gc are supported");
        }
        consume(TokenType.LPAREN, "expected '(' after println");
        Expression arg;
        if (check(TokenType.RPAREN)) {
            arg = LiteralExpression.ofString("", println.location());
        } else {
            arg = parseExpression();
        }
        consume(TokenType.RPAREN, "expected ')' after println argument");
        return new PrintlnExpression(arg, systemTok.location());
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
