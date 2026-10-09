package io.jnative.format;

import io.jnative.diagnostics.DiagnosticReporter;
import io.jnative.lexer.Lexer;
import io.jnative.parser.Parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Deterministic document formatter for the JNative Java subset.
 * Preserves string/number literals, comment text, and the original line ending.
 * Fails closed when the buffer cannot be lexed/parsed safely.
 * @author 张晓达
 */
public final class SourceFormatter {
    public record Options(int indentWidth, boolean insertSpaces) {
        public static Options defaults() {
            return new Options(4, true);
        }
    }

    public record Result(boolean ok, String text, String error) {
        public static Result success(String text) {
            return new Result(true, text, null);
        }

        public static Result fail(String error) {
            return new Result(false, null, error);
        }
    }

    private static final Set<String> SPACE_BEFORE_AFTER = Set.of(
            "=", "==", "!=", "<", ">", "<=", ">=", "+", "-", "*", "/", "%",
            "&&", "||", "&", "|", "^", "->");
    private static final Set<String> NO_SPACE_BEFORE = Set.of(";", ",", ")", "]", "}");
    private static final Set<String> NO_SPACE_AFTER = Set.of("(", "[", ".", "@", "!");

    private final Options options;

    public SourceFormatter() {
        this(Options.defaults());
    }

    public SourceFormatter(Options options) {
        this.options = options == null ? Options.defaults() : options;
    }

    public Result formatDocument(String path, String source) {
        if (source == null) {
            return Result.fail("空文档");
        }
        String eol = source.contains("\r\n") ? "\r\n" : "\n";
        String normalized = source.replace("\r\n", "\n").replace('\r', '\n');

        Lexer compilerLexer = new Lexer(normalized, path == null ? "Memory.java" : path);
        var tokens = compilerLexer.tokenize();
        if (compilerLexer.hasError()) {
            return Result.fail("词法错误，拒绝格式化以免损坏文件");
        }
        DiagnosticReporter reporter = new DiagnosticReporter();
        try {
            new Parser(tokens, reporter).parse(path == null ? "Memory.java" : path);
        } catch (Throwable t) {
            return Result.fail("语法错误，拒绝格式化: " + t.getMessage());
        }
        if (reporter.diagnostics().stream().anyMatch(d ->
                d.severity() == io.jnative.diagnostics.Diagnostic.Severity.ERROR)) {
            return Result.fail("语法错误，拒绝格式化以免损坏文件");
        }
        FormatLexer fl = new FormatLexer(normalized);
        List<FormatToken> raw = fl.tokenize();
        if (fl.hasError()) {
            return Result.fail("格式化词法错误，拒绝格式化");
        }
        List<FormatToken> significant = new ArrayList<>();
        for (FormatToken t : raw) {
            if (t.kind() == FormatToken.Kind.WHITESPACE || t.kind() == FormatToken.Kind.NEWLINE) {
                continue;
            }
            if (t.kind() == FormatToken.Kind.EOF) {
                break;
            }
            if (t.kind() == FormatToken.Kind.ERROR) {
                return Result.fail("格式化词法错误");
            }
            significant.add(t);
        }
        String body = render(significant);
        String out = body.replace("\n", eol);
        if (!source.isEmpty() && source.endsWith("\n") || source.endsWith("\r\n")) {
            if (!out.endsWith(eol)) {
                out = out + eol;
            }
        }
        // Idempotence check
        Result again = formatDocumentOnce(path, out.replace("\r\n", "\n").replace('\r', '\n'), eol);
        if (!again.ok()) {
            return Result.fail("二次格式化失败: " + again.error());
        }
        if (!again.text().equals(out)) {
            return Result.fail("格式化非幂等，已拒绝写入");
        }
        return Result.success(out);
    }

    /** Single pass without idempotence (used internally). */
    private Result formatDocumentOnce(String path, String normalized, String eol) {
        FormatLexer fl = new FormatLexer(normalized);
        List<FormatToken> raw = fl.tokenize();
        if (fl.hasError()) {
            return Result.fail("词法错误");
        }
        List<FormatToken> significant = new ArrayList<>();
        for (FormatToken t : raw) {
            if (t.kind() == FormatToken.Kind.WHITESPACE || t.kind() == FormatToken.Kind.NEWLINE) {
                continue;
            }
            if (t.kind() == FormatToken.Kind.EOF) {
                break;
            }
            significant.add(t);
        }
        return Result.success(render(significant).replace("\n", eol));
    }

    private String render(List<FormatToken> tokens) {
        StringBuilder sb = new StringBuilder();
        int indent = 0;
        String prev = null;
        FormatToken.Kind prevKind = null;
        boolean atLineStart = true;
        boolean justClosedMethodOrType = false;

        for (int i = 0; i < tokens.size(); i++) {
            FormatToken t = tokens.get(i);
            FormatToken next = i + 1 < tokens.size() ? tokens.get(i + 1) : null;
            String text = t.text();

            if (t.kind() == FormatToken.Kind.LINE_COMMENT) {
                if (!atLineStart && sb.length() > 0 && sb.charAt(sb.length() - 1) != ' ') {
                    sb.append(' ');
                }
                if (atLineStart) {
                    sb.append(indentStr(indent));
                }
                sb.append(text);
                sb.append('\n');
                atLineStart = true;
                prev = text;
                prevKind = t.kind();
                continue;
            }
            if (t.kind() == FormatToken.Kind.BLOCK_COMMENT) {
                if (atLineStart) {
                    sb.append(indentStr(indent));
                } else if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ' '
                        && sb.charAt(sb.length() - 1) != '\n') {
                    sb.append(' ');
                }
                // Keep block comment text; normalize internal newlines with indent for leading *
                String[] lines = text.split("\n", -1);
                for (int li = 0; li < lines.length; li++) {
                    if (li > 0) {
                        sb.append('\n').append(indentStr(indent));
                        String line = lines[li];
                        if (line.startsWith(" *") || line.startsWith(" *") || line.trim().startsWith("*")) {
                            sb.append(line.stripLeading().startsWith("*")
                                    ? " " + line.stripLeading()
                                    : line.stripLeading());
                        } else {
                            sb.append(line.stripLeading());
                        }
                    } else {
                        sb.append(lines[li]);
                    }
                }
                if (next != null && next.kind() != FormatToken.Kind.PUNCT) {
                    sb.append('\n');
                    atLineStart = true;
                } else {
                    atLineStart = false;
                }
                prev = text;
                prevKind = t.kind();
                continue;
            }

            if ("}".equals(text)) {
                indent = Math.max(0, indent - 1);
            }

            // Blank line before class/method members after closing }
            if (justClosedMethodOrType && isTypeOrMethodStart(t, next)) {
                if (!atLineStart) {
                    sb.append('\n');
                }
                sb.append('\n');
                atLineStart = true;
            }
            justClosedMethodOrType = false;

            if (atLineStart) {
                sb.append(indentStr(indent));
                atLineStart = false;
            } else if (needsSpace(prev, prevKind, text, t.kind())) {
                sb.append(' ');
            }

            sb.append(text);

            if ("{".equals(text)) {
                indent++;
                sb.append('\n');
                atLineStart = true;
            } else if ("}".equals(text)) {
                sb.append('\n');
                atLineStart = true;
                justClosedMethodOrType = true;
            } else if (";".equals(text)) {
                sb.append('\n');
                atLineStart = true;
            } else if (":".equals(text) && prevKind == FormatToken.Kind.KEYWORD
                    && ("case".equals(prev) || "default".equals(prev))) {
                sb.append('\n');
                atLineStart = true;
            }

            prev = text;
            prevKind = t.kind();
        }
        String s = sb.toString();
        // Trim trailing spaces on each line; keep final newline policy to caller
        StringBuilder clean = new StringBuilder();
        for (String line : s.split("\n", -1)) {
            int end = line.length();
            while (end > 0 && (line.charAt(end - 1) == ' ' || line.charAt(end - 1) == '\t')) {
                end--;
            }
            clean.append(line, 0, end).append('\n');
        }
        // remove extra trailing newline added by split loop if input had none — caller handles
        if (clean.length() > 0) {
            clean.setLength(clean.length() - 1);
        }
        return clean.toString();
    }

    private boolean isTypeOrMethodStart(FormatToken t, FormatToken next) {
        if (t.kind() != FormatToken.Kind.KEYWORD && t.kind() != FormatToken.Kind.IDENT) {
            return false;
        }
        String x = t.text();
        return "public".equals(x) || "private".equals(x) || "protected".equals(x)
                || "static".equals(x) || "class".equals(x) || "interface".equals(x)
                || "void".equals(x) || "int".equals(x) || "long".equals(x)
                || "boolean".equals(x) || "final".equals(x) || "abstract".equals(x)
                || "async".equals(x) || (t.kind() == FormatToken.Kind.IDENT && next != null
                && "(".equals(next.text()));
    }

    private boolean needsSpace(String prev, FormatToken.Kind prevKind, String cur, FormatToken.Kind curKind) {
        if (prev == null) {
            return false;
        }
        if (NO_SPACE_AFTER.contains(prev)) {
            return false;
        }
        if (NO_SPACE_BEFORE.contains(cur)) {
            return false;
        }
        if (".".equals(prev) || ".".equals(cur)) {
            return false;
        }
        if ("(".equals(cur) && (prevKind == FormatToken.Kind.IDENT || prevKind == FormatToken.Kind.KEYWORD
                || ")".equals(prev) || "]".equals(prev))) {
            // method call / if(
            if ("if".equals(prev) || "for".equals(prev) || "while".equals(prev)
                    || "switch".equals(prev) || "catch".equals(prev)) {
                return true;
            }
            return false;
        }
        if (SPACE_BEFORE_AFTER.contains(cur) || SPACE_BEFORE_AFTER.contains(prev)) {
            // unary +/- : no space after = ( or ,
            if (("+".equals(cur) || "-".equals(cur))
                    && ("(".equals(prev) || ",".equals(prev) || "=".equals(prev)
                    || "return".equals(prev))) {
                return false;
            }
            return true;
        }
        if (",".equals(prev)) {
            return true;
        }
        if (prevKind == FormatToken.Kind.KEYWORD && curKind != FormatToken.Kind.PUNCT) {
            return true;
        }
        if (prevKind == FormatToken.Kind.IDENT && curKind == FormatToken.Kind.IDENT) {
            return true;
        }
        if (prevKind == FormatToken.Kind.IDENT && curKind == FormatToken.Kind.KEYWORD) {
            return true;
        }
        if ((prevKind == FormatToken.Kind.LITERAL || prevKind == FormatToken.Kind.STRING)
                && curKind != FormatToken.Kind.PUNCT) {
            return true;
        }
        return false;
    }

    private String indentStr(int level) {
        if (level <= 0) {
            return "";
        }
        if (options.insertSpaces()) {
            return " ".repeat(Math.max(0, options.indentWidth()) * level);
        }
        return "\t".repeat(level);
    }
}
