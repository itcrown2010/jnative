/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.stdlib;

import io.jnative.compiler.SourceUnit;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Merges bundled JNative stdlib sources into a compilation unit.
 * HTTP / UI / Collections are implemented in JNative (not compiler intrinsics).
 * @author 张晓达
 */
public final class StdlibIncluder {
    private StdlibIncluder() {
    }

    public static boolean needsHttp(String sourceText) {
        if (sourceText == null) {
            return false;
        }
        return sourceText.contains("HttpClient")
                || sourceText.contains("HttpServer")
                || sourceText.contains("HttpRequest")
                || sourceText.contains("HttpResponse")
                || sourceText.contains("HttpConnection")
                || sourceText.contains("HttpUrl")
                || sourceText.contains("HttpHeaders")
                || sourceText.contains("HttpException")
                || needsWeb(sourceText);
    }

    public static boolean needsWeb(String sourceText) {
        if (sourceText == null) {
            return false;
        }
        return sourceText.contains("jnative.web")
                || sourceText.contains("WebApp")
                || sourceText.contains("WebContext")
                || sourceText.contains("WebRequest")
                || sourceText.contains("WebResponse")
                || sourceText.contains("WebServer")
                || sourceText.contains("WebConfig")
                || sourceText.contains("ServerConfig")
                || sourceText.contains("listenTls")
                || sourceText.contains("WebMiddleware")
                || sourceText.contains("WebHandler")
                || sourceText.contains("AsyncWebHandler")
                || sourceText.contains("WebException");
    }

    public static boolean needsBrowser(String sourceText) {
        if (sourceText == null) {
            return false;
        }
        return sourceText.contains("jnative.browser")
                || sourceText.contains("Browser.console")
                || sourceText.contains("Browser.document")
                || sourceText.contains("Browser.window")
                || sourceText.contains("Browser.localStorage")
                || sourceText.contains("new Document(")
                || sourceText.contains("HtmlButtonElement")
                || sourceText.contains("HtmlInputElement")
                || needsWebui(sourceText);
    }

    public static boolean needsWebui(String sourceText) {
        if (sourceText == null) {
            return false;
        }
        return sourceText.contains("jnative.webui")
                || sourceText.contains("WebUI.mount")
                || sourceText.contains("WebUI.stateInt")
                || sourceText.contains("WebUI.stateString")
                || sourceText.contains("Html.div2")
                || sourceText.contains("Html.h1Dynamic")
                || sourceText.contains("Html.buttonLabel")
                || sourceText.contains("WebUiComponent")
                || sourceText.contains("extends WebUiComponent");
    }

    public static boolean needsRpc(String sourceText) {
        if (sourceText == null) {
            return false;
        }
        return sourceText.contains("@Rpc")
                || sourceText.contains("jnative.rpc")
                || sourceText.contains("RpcHttp.")
                || sourceText.contains("RpcEnvelope.")
                || sourceText.contains("_Rpc.mount")
                || sourceText.contains("_Rpc.client");
    }

    public static String prependRpcStdlib(String userSource) throws IOException {
        if (userSource.contains("// JNative RPC Runtime (0.28)")) {
            return userSource;
        }
        return load("stdlib/rpc/RpcLib.java") + "\n\n" + userSource;
    }

    public static boolean needsUi(String sourceText) {
        if (sourceText == null) {
            return false;
        }
        return sourceText.contains("jnative.ui")
                || sourceText.contains("new Window(")
                || sourceText.contains("new Button(")
                || sourceText.contains("new Label(")
                || sourceText.contains("new TextField(")
                || sourceText.contains("new TextArea(")
                || sourceText.contains("new VBox(")
                || sourceText.contains("new HBox(")
                || sourceText.contains("new GridPane(")
                || sourceText.contains("new CheckBox(")
                || sourceText.contains("new RadioButton(")
                || sourceText.contains("new ComboBox")
                || sourceText.contains("new ListView")
                || sourceText.contains("new TableView")
                || sourceText.contains("new TreeView")
                || sourceText.contains("new TabPane(")
                || sourceText.contains("new SplitPane(")
                || sourceText.contains("new ScrollView(")
                || sourceText.contains("new MenuBar(")
                || sourceText.contains("new ProgressBar(")
                || sourceText.contains("FileDialog.")
                || sourceText.contains("DirectoryDialog.")
                || sourceText.contains("PopupWindow")
                || sourceText.contains("Dialogs.")
                || sourceText.contains("StudioApplication")
                || sourceText.contains("jnative.studio")
                || sourceText.contains("UiTest.")
                || sourceText.contains("UiTestHarness")
                || sourceText.contains("UiThreadViolationException")
                || sourceText.contains("Application.invokeLater")
                || sourceText.contains("new Application(")
                || sourceText.contains("new Canvas(")
                || sourceText.contains("new UiTimer(")
                || sourceText.contains("new CodeEditor(")
                || sourceText.contains("ObservableValue")
                || sourceText.contains("ObservableInt")
                || sourceText.contains("BindingScope")
                || sourceText.contains("Bindings.")
                || sourceText.contains("FormModel")
                || sourceText.contains("TrendChart");
    }

    private static final java.util.regex.Pattern SYSTEM_SHIM = java.util.regex.Pattern.compile(
            "System\\s*\\.\\s*(exit|err|currentTimeMillis|nanoTime|arraycopy|lineSeparator)\\b"
                    + "|System\\s*\\.\\s*out\\s*\\.\\s*print\\s*\\(");

    /** {@code System.exit/err/print/currentTimeMillis/nanoTime/arraycopy/lineSeparator} → {@code JnSystem}. */
    public static boolean needsSystem(String sourceText) {
        return sourceText != null && SYSTEM_SHIM.matcher(sourceText).find();
    }

    public static boolean needsMath(String sourceText) {
        if (sourceText == null) {
            return false;
        }
        if (sourceText.contains("// JNative Math stdlib")) {
            return false;
        }
        return sourceText.contains("Math.")
                || sourceText.contains("Math ")
                || sourceText.contains("(Math)")
                || sourceText.contains("jnative.lang.Math")
                || sourceText.contains("jnative.math")
                || needsDecimal(sourceText);
    }

    public static boolean needsDecimal(String sourceText) {
        if (sourceText == null) {
            return false;
        }
        if (sourceText.contains("// JNative Decimal Core")) {
            return false;
        }
        return sourceText.contains("Decimal.")
                || sourceText.contains("new Decimal(")
                || sourceText.contains("Decimal ")
                || sourceText.contains("jnative.math.Decimal");
    }

    public static String prependMathStdlib(String userSource) throws IOException {
        if (userSource.contains("// JNative Math stdlib")) {
            return userSource;
        }
        String math = load("stdlib/math/MathLib.java");
        if (needsDecimal(userSource) || userSource.contains("Decimal")) {
            math = math + "\n\n" + load("stdlib/math/DecimalLib.java");
        }
        return math + "\n\n" + userSource;
    }

    public static boolean needsCollections(String sourceText) {
        if (sourceText == null) {
            return false;
        }
        return sourceText.contains("jnative.collections")
                || sourceText.contains("jnative.function")
                || sourceText.contains("ArrayList")
                || sourceText.contains("HashMap")
                || sourceText.contains("HashSet")
                || sourceText.contains("ArrayDeque")
                || sourceText.contains("StringBuilder")
                || sourceText.contains("Iterable<")
                || sourceText.contains("Iterator<")
                || sourceText.contains("Comparator<")
                || sourceText.contains("Consumer<")
                || sourceText.contains("Predicate<")
                || sourceText.contains("Supplier<")
                || sourceText.contains("Function<")
                || sourceText.contains("AutoCloseable")
                || sourceText.contains("try (")
                || sourceText.contains("new ArrayList")
                || sourceText.contains("new HashMap")
                || sourceText.contains("new HashSet")
                || sourceText.contains("new ArrayDeque")
                || sourceText.contains("new StringBuilder")
                || sourceText.contains("Collections.")
                || sourceText.contains("Arrays.")
                || sourceText.contains("BiFunction<")
                || sourceText.contains("BiConsumer<")
                || sourceText.contains("UnsupportedOperationException")
                || sourceText.contains("Map.Entry")
                || usesCollectionType(sourceText, "List")
                || usesCollectionType(sourceText, "Set")
                || usesCollectionType(sourceText, "Map")
                || usesCollectionType(sourceText, "Collection")
                || usesCollectionType(sourceText, "Queue")
                || usesCollectionType(sourceText, "Deque")
                || usesCollectionType(sourceText, "LinkedList")
                || usesCollectionType(sourceText, "Stack")
                || usesCollectionType(sourceText, "PriorityQueue")
                || needsObjects(sourceText)
                || needsOptional(sourceText)
                || needsRandom(sourceText)
                || needsBoxing(sourceText);
    }

    /**
     * True when {@code Name<..>} / {@code Name.of(} appears and the program does not declare its own
     * type of that name (a user-defined {@code class List} must keep working).
     */
    private static boolean usesCollectionType(String src, String name) {
        if (src.contains("class " + name + " ") || src.contains("class " + name + "<")
                || src.contains("interface " + name + " ") || src.contains("interface " + name + "<")) {
            return false;
        }
        return src.contains(name + "<") || src.contains(name + ".of(") || src.contains(name + ".copyOf(");
    }

    private static final java.util.regex.Pattern OBJECTS_USE =
            java.util.regex.Pattern.compile("(?<![A-Za-z0-9_$.])Objects\\s*\\.");
    private static final java.util.regex.Pattern OPTIONAL_USE =
            java.util.regex.Pattern.compile("(?<![A-Za-z0-9_$.])Optional\\s*[<.]");
    private static final java.util.regex.Pattern RANDOM_USE =
            java.util.regex.Pattern.compile("(?<![A-Za-z0-9_$])Random\\b");

    private static boolean declaresType(String src, String name) {
        return src.contains("class " + name + " ") || src.contains("class " + name + "<")
                || src.contains("class " + name + "{") || src.contains("class " + name + "\n")
                || src.contains("interface " + name + " ") || src.contains("interface " + name + "<");
    }

    public static boolean needsObjects(String src) {
        return src != null && OBJECTS_USE.matcher(src).find() && !declaresType(src, "Objects");
    }

    public static boolean needsOptional(String src) {
        return src != null && OPTIONAL_USE.matcher(src).find() && !declaresType(src, "Optional");
    }

    public static boolean needsRandom(String src) {
        return src != null && RANDOM_USE.matcher(src).find() && !declaresType(src, "Random");
    }

    /** Boxed primitives and types that typically imply autobox. */
    public static boolean needsBoxing(String sourceText) {
        if (sourceText == null) {
            return false;
        }
        return sourceText.contains("Integer")
                // primitives box into Object ({@code Object o = 5}) via the wrapper classes
                || sourceText.contains("Object")
                || sourceText.contains("Boolean")
                || sourceText.contains("Float")
                || sourceText.contains("Double")
                // "Long" alone is too broad (identifiers); require type-like uses
                || sourceText.contains("Long ")
                || sourceText.contains("Long>")
                || sourceText.contains("Long,")
                || sourceText.contains("<Long")
                || sourceText.contains("Long.")
                || sourceText.contains("(Long)")
                || sourceText.contains("new Long")
                || sourceText.contains("NumberFormatException")
                // A user-defined class Character (e.g. a game entity) must keep working.
                || (sourceText.contains("Character") && !sourceText.contains("class Character"));
    }

    public static boolean needsGraphics(String sourceText) {
        if (sourceText == null) {
            return false;
        }
        return sourceText.contains("jnative.graphics")
                || sourceText.contains("new Canvas(")
                || sourceText.contains("new Graphics(")
                || sourceText.contains("Image.create(")
                || sourceText.contains("Image.load(")
                || sourceText.contains("new Color(")
                || sourceText.contains("Color.BLUE")
                || sourceText.contains("Color.RED")
                || sourceText.contains("Color.BLACK")
                || sourceText.contains("Color.WHITE")
                || sourceText.contains("new Font(")
                || sourceText.contains("Font.monospace")
                || sourceText.contains("new Path(")
                || sourceText.contains("new Stroke(")
                || sourceText.contains("PaintHandler")
                || sourceText.contains("new UiTimer(")
                || sourceText.contains("GfxMetrics.")
                || sourceText.contains("beginGraphics()")
                || sourceText.contains("new CodeEditor(")
                || sourceText.contains("jnative.editor");
    }

    public static boolean needsEditor(String sourceText) {
        if (sourceText == null) {
            return false;
        }
        return sourceText.contains("jnative.editor")
                || sourceText.contains("new CodeEditor(")
                || sourceText.contains("new TextDocument(")
                || sourceText.contains("TextDocument.load(")
                || sourceText.contains("new PieceTable(")
                || sourceText.contains("JNativeSyntaxTokenizer")
                || sourceText.contains("UndoManager")
                || sourceText.contains("TextSearch.")
                || sourceText.contains("EditorTheme.")
                || sourceText.contains("WordBoundary.")
                || sourceText.contains("EditorClock.")
                || sourceText.contains("ProcessRunner.")
                || sourceText.contains("EditorDiagnostic.")
                || sourceText.contains("StudioApplication")
                || sourceText.contains("jnative.studio");
    }

    /** Text clipboard only — does not pull Editor/Graphics (avoids Point/Size/Rect clash with UI). */
    public static boolean needsClipboard(String sourceText) {
        if (sourceText == null) {
            return false;
        }
        return sourceText.contains("Clipboard.setText")
                || sourceText.contains("Clipboard.getText");
    }

    public static String prependClipboardStdlib(String userSource) throws IOException {
        if (userSource.contains("// JNative Clipboard (text)")) {
            return userSource;
        }
        /* Full EditorLib already defines Clipboard. */
        if (userSource.contains("class Clipboard ")) {
            return userSource;
        }
        return load("stdlib/editor/ClipboardLib.java") + "\n\n" + userSource;
    }

    public static boolean needsJson(String sourceText) {
        if (sourceText == null) {
            return false;
        }
        return sourceText.contains("JsonParser")
                || sourceText.contains("JsonWriter")
                || sourceText.contains("JsonObject")
                || sourceText.contains("JsonCodec")
                || sourceText.contains("Json.stringify")
                || sourceText.contains("Json.parse")
                || needsWeb(sourceText)
                || sourceText.contains("jnative.json")
                || sourceText.contains("StudioApplication")
                || sourceText.contains("jnative.studio");
    }

    public static boolean needsTime(String sourceText) {
        if (sourceText == null) {
            return false;
        }
        return sourceText.contains("jnative.time")
                || sourceText.contains("Instant.")
                || sourceText.contains("new Instant(")
                || sourceText.contains("Duration.")
                || sourceText.contains("LocalDate.")
                || sourceText.contains("LocalTime.")
                || sourceText.contains("LocalDateTime.")
                || sourceText.contains("ZoneId.")
                || sourceText.contains("ZoneOffset.")
                || sourceText.contains("ZonedDateTime.")
                || sourceText.contains("DateTimeFormatter.")
                || sourceText.contains("Clock.");
    }

    public static boolean needsDatabase(String sourceText) {
        if (sourceText == null) {
            return false;
        }
        return sourceText.contains("jnative.db")
                || sourceText.contains("Database.connect")
                || sourceText.contains("Database.connectWithConfig")
                || sourceText.contains("DatabaseConfig")
                || sourceText.contains("DatabaseUrl")
                || sourceText.contains("RowMapper")
                || sourceText.contains("DbConnection")
                || sourceText.contains("DbQueryResult")
                || sourceText.contains("ConnectionPool")
                || sourceText.contains("DatabaseDriver")
                || sourceText.contains("DbByteBuffer")
                || sourceText.contains("SQLiteConnection")
                || sourceText.contains("SQLiteDriver")
                || sourceText.contains("sqlite3_")
                || sourceText.contains("Sqlite3Native")
                || sourceText.contains("PostgreSQLDriver")
                || sourceText.contains("PgConnection")
                || sourceText.contains("PgMessageReader")
                || sourceText.contains("DbSqliteBootstrap")
                || sourceText.contains("DbPgBootstrap")
                || sourceText.contains("Migrator")
                || sourceText.contains("PgProtocolTestKit")
                || sourceText.contains("postgresql://")
                || sourceText.contains("sqlite:");
    }

    public static boolean needsAsync(String sourceText) {
        if (sourceText == null) {
            return false;
        }
        return sourceText.contains("jnative.async")
                || sourceText.contains("EventLoop")
                || sourceText.contains("EventLoopGroup")
                || sourceText.contains("JnFuture")
                || sourceText.contains("Future.")
                || sourceText.contains("new Future(")
                || sourceText.contains("Async.")
                || sourceText.contains("AsyncMutex")
                || sourceText.contains("AsyncSemaphore")
                || sourceText.contains("AsyncQueue")
                || sourceText.contains("AsyncTcpSocket")
                || sourceText.contains("AsyncTcpServer")
                || sourceText.contains("CancellationSource")
                || sourceText.contains("TimeoutException")
                || sourceText.contains("FutureBlocker")
                || sourceText.contains(" async ")
                || sourceText.contains(" async\n")
                || sourceText.contains(" async(")
                || sourceText.contains(" await ")
                || sourceText.contains(" await\n")
                || sourceText.contains(" await(")
                || needsDatabase(sourceText);
    }

    public static boolean needsIo(String sourceText) {
        if (sourceText == null) {
            return false;
        }
        return sourceText.contains("Path.of")
                || sourceText.contains("FsPath.of")
                || sourceText.contains("Files.")
                || sourceText.contains("FsNative.")
                || sourceText.contains("FileInfo")
                || sourceText.contains("FileSystem.")
                || sourceText.contains("SystemPaths.")
                || sourceText.contains("WatchService")
                || sourceText.contains("FileSize.")
                || sourceText.contains("ProcessBuilder")
                || sourceText.contains("new Process(")
                || sourceText.contains("new ProcessBuilder(")
                || sourceText.contains("Logger.")
                || sourceText.contains("Desktop.")
                || sourceText.contains("jnative.io")
                || sourceText.contains("Env.")
                || sourceText.contains("CpuInfo.")
                || sourceText.contains("StudioApplication")
                || sourceText.contains("jnative.studio")
                || sourceText.contains("ByteBuffer")
                || sourceText.contains("InputStream")
                || sourceText.contains("OutputStream")
                || sourceText.contains("FileInputStream")
                || sourceText.contains("FileOutputStream")
                || sourceText.contains("BufferedInputStream")
                || sourceText.contains("BufferedOutputStream")
                || sourceText.contains("BufferedReader")
                || sourceText.contains("BufferedWriter")
                || sourceText.contains("ByteArrayInputStream")
                || sourceText.contains("ByteArrayOutputStream")
                || sourceText.contains("ByteBufferPool")
                || sourceText.contains("StandardOpenOption")
                || sourceText.contains("writeAtomic")
                || needsNet(sourceText)
                || needsWeb(sourceText);
    }

    public static boolean needsNet(String sourceText) {
        if (sourceText == null) {
            return false;
        }
        return sourceText.contains("DnsResolver")
                || sourceText.contains("UdpSocket")
                || sourceText.contains("IPAddress")
                || sourceText.contains("SocketAddress")
                || sourceText.contains("NetNative");
    }

    public static boolean needsStudio(String sourceText) {
        if (sourceText == null) {
            return false;
        }
        return sourceText.contains("StudioApplication")
                || sourceText.contains("jnative.studio")
                || sourceText.contains("JNativeStudio")
                || sourceText.contains("new Project(")
                || sourceText.contains("Project.load(")
                || sourceText.contains("new ConsoleBuffer(")
                || sourceText.contains("new EditorTab(")
                || sourceText.contains("BuildManager")
                || sourceText.contains("StudioSettings")
                || sourceText.contains("StudioDiagnostic")
                || sourceText.contains("WorkspaceIndex")
                || sourceText.contains("CompletionService")
                || sourceText.contains("CompletionItem")
                || sourceText.contains("new CompletionItem(")
                || sourceText.contains("WorkspaceIndex")
                || sourceText.contains("StudioSymbol")
                || sourceText.contains("ProjectSearchService");
    }

    public static boolean needsTls(String sourceText) {
        if (sourceText == null) {
            return false;
        }
        return sourceText.contains("TlsConnection")
                || sourceText.contains("listenTls")
                || sourceText.contains("TlsConfig")
                || sourceText.contains("TlsContext")
                || sourceText.contains("jnative.tls");
    }

    public static boolean needsHttp2(String sourceText) {
        if (sourceText == null) {
            return false;
        }
        return sourceText.contains("Http2Client")
                || sourceText.contains("Http2Connection")
                || sourceText.contains("Http2Codec")
                || sourceText.contains("HpackEncoder")
                || sourceText.contains("jnative.http2");
    }

    public static String prependTlsStdlib(String userSource) throws IOException {
        if (userSource.contains("// JNative TLS stdlib (0.31)")) {
            return userSource;
        }
        return load("stdlib/tls/TlsLib.java") + "\n\n" + userSource;
    }

    public static String prependHttp2Stdlib(String userSource) throws IOException {
        if (userSource.contains("// JNative HTTP/2 (0.31)")) {
            return userSource;
        }
        return load("stdlib/http/Http2Lib.java") + "\n\n" + userSource;
    }

    public static String prependHttpStdlib(String userSource) throws IOException {
        List<String> parts = new ArrayList<>();
        parts.add(load("stdlib/http/HttpLib.java"));
        parts.add(userSource);
        return String.join("\n\n", parts);
    }

    public static boolean needsCrypto(String sourceText) {
        if (sourceText == null) {
            return false;
        }
        return sourceText.contains("jnative.crypto")
                || sourceText.contains("SecureRandom")
                || sourceText.contains("Sha1")
                || sourceText.contains("Sha256")
                || sourceText.contains("Base64Codec")
                || sourceText.contains("Password.hash")
                || sourceText.contains("Password.verify")
                || sourceText.contains("Jwt.")
                || sourceText.contains("WebSocketAccept")
                || sourceText.contains("ConstantTime")
                || sourceText.contains("Hmac.")
                || needsRealtime(sourceText)
                || needsAuth(sourceText);
    }

    public static boolean needsRealtime(String sourceText) {
        if (sourceText == null) {
            return false;
        }
        return sourceText.contains("WebSocket")
                || sourceText.contains("websocket")
                || sourceText.contains("WebSocketHub")
                || sourceText.contains("SseStream")
                || sourceText.contains("SseEvent")
                || sourceText.contains("ctx.sse");
    }

    public static boolean needsAuth(String sourceText) {
        if (sourceText == null) {
            return false;
        }
        return sourceText.contains("SessionManager")
                || sourceText.contains("UserPrincipal")
                || sourceText.contains("AuthRequired")
                || sourceText.contains("AuthRequireRole")
                || sourceText.contains("InMemorySessionStore")
                || sourceText.contains("Csrf.")
                || sourceText.contains("RateLimiter")
                || sourceText.contains("SecurityHeaders")
                || sourceText.contains("UnauthorizedException")
                || sourceText.contains("ForbiddenException")
                || sourceText.contains("AuthLogin");
    }

    public static String prependCryptoStdlib(String userSource) throws IOException {
        if (userSource.contains("// JNative Crypto (0.25)")) {
            return userSource;
        }
        return load("stdlib/crypto/CryptoLib.java") + "\n\n" + userSource;
    }

    public static String prependWebStdlib(String userSource) throws IOException {
        List<String> parts = new ArrayList<>();
        parts.add(load("stdlib/web/WebLib.java"));
        parts.add(load("stdlib/web/RealtimeLib.java"));
        parts.add(load("stdlib/web/AuthLib.java"));
        parts.add(userSource);
        return String.join("\n\n", parts);
    }

    public static String prependUiStdlib(String userSource) throws IOException {
        List<String> parts = new ArrayList<>();
        parts.add(load("stdlib/ui/UiLib.java"));
        parts.add(load("stdlib/ui/BindingLib.java"));
        parts.add(userSource);
        return String.join("\n\n", parts);
    }

    public static String prependCollectionsStdlib(String userSource) throws IOException {
        List<String> parts = new ArrayList<>();
        parts.add(load("stdlib/collections/CollectionsLib.java"));
        parts.add(userSource);
        return String.join("\n\n", parts);
    }

    public static String prependGraphicsStdlib(String userSource) throws IOException {
        List<String> parts = new ArrayList<>();
        parts.add(load("stdlib/graphics/GraphicsLib.java"));
        parts.add(userSource);
        return String.join("\n\n", parts);
    }

    public static String prependEditorStdlib(String userSource) throws IOException {
        List<String> parts = new ArrayList<>();
        parts.add(load("stdlib/editor/EditorLib.java"));
        parts.add(userSource);
        return String.join("\n\n", parts);
    }

    public static String prependJsonStdlib(String userSource) throws IOException {
        List<String> parts = new ArrayList<>();
        parts.add(load("stdlib/json/JsonLib.java"));
        parts.add(userSource);
        return String.join("\n\n", parts);
    }

    public static String prependIoStdlib(String userSource) throws IOException {
        List<String> parts = new ArrayList<>();
        parts.add(load("stdlib/io/StreamLib.java"));
        parts.add(load("stdlib/io/IoLib.java"));
        if (needsNet(userSource)
                || userSource.contains("UdpSocket")
                || userSource.contains("DnsResolver")
                || userSource.contains("IPAddress")) {
            parts.add(load("stdlib/net/NetLib.java"));
        }
        parts.add(userSource);
        return String.join("\n\n", parts);
    }

    /** Inserts TimeLib before primary public class when not already present. */
    public static String insertTimeStdlib(String mergedSource) throws IOException {
        if (mergedSource.contains("// JNative Time Core (Round 2)")
                || mergedSource.contains("// JNative time subset")) {
            return mergedSource;
        }
        String block = load("stdlib/time/TimeLib.java");
        int userIdx = indexOfPrimaryPublicClass(mergedSource);
        if (userIdx > 0) {
            return mergedSource.substring(0, userIdx) + block + "\n\n" + mergedSource.substring(userIdx);
        }
        return block + "\n\n" + mergedSource;
    }

    /** Inserts DbLib + SQLite (+ optional PostgreSQL) before user source. */
    public static String insertDatabaseStdlib(String mergedSource) throws IOException {
        if (mergedSource.contains("// JNative SQLite driver (0.24)")) {
            return mergedSource;
        }
        String block = load("stdlib/db/DbLib.java")
                + "\n\n" + load("stdlib/db/SqliteDriverLib.java")
                + "\n\n" + load("stdlib/db/DbMigrateLib.java")
                /* Always ship PG driver with DB stdlib — DbBootstrap registers both.
                   (Previously gated on DbPgBootstrap.register in user source, so
                   postgresql:// URLs failed with "no driver registered".) */
                + "\n\n" + load("stdlib/db/PgDriverLib.java");
        int userIdx = indexOfPrimaryPublicClass(mergedSource);
        if (userIdx > 0) {
            return mergedSource.substring(0, userIdx) + block + "\n\n" + mergedSource.substring(userIdx);
        }
        return mergedSource + "\n\n" + block;
    }

    /** Inserts AsyncLib after CollectionsLib (Function/ArrayList required by AsyncLib). */
    public static String insertAsyncStdlib(String mergedSource) throws IOException {
        String async = load("stdlib/async/AsyncLib.java");
        String collections = load("stdlib/collections/CollectionsLib.java");
        String prefix = collections + "\n\n";
        if (mergedSource.startsWith(prefix)) {
            return prefix + async + "\n\n" + mergedSource.substring(prefix.length());
        }
        if (mergedSource.contains("class ArrayList") && mergedSource.contains("class ThreadPoolExecutor")) {
            /* Collections already merged but prefix bytes differ — append async after collections marker. */
            int cut = mergedSource.indexOf("class ThreadPoolWorker");
            if (cut > 0) {
                int end = mergedSource.indexOf("\n}", cut);
                if (end > 0) {
                    end = end + 2;
                    return mergedSource.substring(0, end) + "\n\n" + async + "\n\n"
                            + mergedSource.substring(end);
                }
            }
        }
        return prefix + async + "\n\n" + mergedSource;
    }

    public static String prependStudioStdlib(String userSource) throws IOException {
        List<String> parts = new ArrayList<>();
        parts.add(load("stdlib/studio/StudioLib.java"));
        parts.add(userSource);
        return String.join("\n\n", parts);
    }

    /**
     * Collect selected stdlib resources as independent {@link SourceUnit}s (no text concat).
     * Order is dependency-friendly for deterministic IR; resolution does not rely on it.
     */
    public static List<SourceUnit> collectStdlibSourceUnits(String userSource) throws IOException {
        Set<String> resources = new LinkedHashSet<>();
        boolean wantStudio = needsStudio(userSource);
        boolean wantEditor = needsEditor(userSource) || wantStudio;
        boolean wantClipboard = needsClipboard(userSource);
        boolean wantGraphics = needsGraphics(userSource) || wantEditor;
        boolean wantUi = needsUi(userSource) || wantGraphics || wantEditor || wantStudio || wantClipboard;
        boolean wantWeb = needsWeb(userSource);
        boolean wantJson = needsJson(userSource) || wantStudio || wantWeb;
        boolean wantIo = needsIo(userSource) || wantStudio || wantWeb
                || needsHttp(userSource) || needsHttp2(userSource);
        boolean wantBrowser = needsBrowser(userSource);
        boolean wantDatabase = needsDatabase(userSource);
        boolean wantCrypto = needsCrypto(userSource) || wantWeb || needsHttp(userSource) || needsHttp2(userSource)
                || needsTls(userSource)
                || (userSource != null && userSource.contains("BitOps."))
                || needsDecimal(userSource);
        boolean wantRpc = needsRpc(userSource);
        boolean wantTime = needsTime(userSource);
        boolean wantAsync = needsAsync(userSource) || wantWeb || wantDatabase || wantCrypto || wantBrowser || wantRpc
                || wantTime;
        boolean wantMath = needsMath(userSource);
        boolean wantTlsEarly = needsTls(userSource) || needsHttp(userSource) || needsHttp2(userSource) || wantWeb;
        if (needsCollections(userSource) || wantUi || wantGraphics || wantEditor || wantJson || wantIo || wantStudio
                || wantAsync || wantDatabase || wantCrypto || wantRpc || wantTime || wantTlsEarly || needsNet(userSource)) {
            resources.add("stdlib/collections/BoxingLib.java");
            resources.add("stdlib/collections/CollectionsLib.java");
            if (needsObjects(userSource)) {
                resources.add("stdlib/collections/ObjectsLib.java");
            }
            if (needsOptional(userSource)) {
                resources.add("stdlib/collections/OptionalLib.java");
            }
            if (needsRandom(userSource)) {
                resources.add("stdlib/collections/RandomLib.java");
            }
        } else if (needsBoxing(userSource)) {
            resources.add("stdlib/collections/BoxingLib.java");
        }
        if (needsSystem(userSource)) {
            resources.add("stdlib/system/SystemLib.java");
        }
        if (wantMath || needsMath(userSource)) {
            resources.add("stdlib/math/MathLib.java");
            if (needsDecimal(userSource) || (userSource != null && userSource.contains("Decimal"))) {
                resources.add("stdlib/math/DecimalLib.java");
            }
        }
        if (wantAsync) {
            resources.add("stdlib/async/AsyncLib.java");
        }
        if (wantDatabase) {
            resources.add("stdlib/db/DbLib.java");
            resources.add("stdlib/db/SqliteDriverLib.java");
            resources.add("stdlib/db/DbMigrateLib.java");
            resources.add("stdlib/db/PgDriverLib.java");
        }
        if (wantTime || needsTime(userSource)) {
            resources.add("stdlib/time/TimeLib.java");
        }
        if (wantCrypto || needsCrypto(userSource)) {
            resources.add("stdlib/crypto/CryptoLib.java");
        }
        if (wantJson || needsJson(userSource) || wantRpc) {
            resources.add("stdlib/json/JsonLib.java");
        }
        if (wantIo || needsIo(userSource) || needsNet(userSource)) {
            resources.add("stdlib/io/StreamLib.java");
            resources.add("stdlib/io/IoLib.java");
            if (needsNet(userSource)) {
                resources.add("stdlib/net/NetLib.java");
            }
        }
        if (wantUi || needsUi(userSource)) {
            resources.add("stdlib/ui/UiLib.java");
            resources.add("stdlib/ui/BindingLib.java");
            /* BindingLib DecimalFieldModel */
            resources.add("stdlib/math/MathLib.java");
            resources.add("stdlib/math/DecimalLib.java");
        }
        if (wantGraphics || needsGraphics(userSource)) {
            resources.add("stdlib/graphics/GraphicsLib.java");
        }
        if (wantEditor || needsEditor(userSource)) {
            resources.add("stdlib/editor/EditorLib.java");
        } else if (wantClipboard || needsClipboard(userSource)) {
            resources.add("stdlib/editor/ClipboardLib.java");
        }
        if (wantStudio || needsStudio(userSource)) {
            resources.add("stdlib/studio/StudioLib.java");
        }
        boolean wantHttpStack = needsHttp(userSource) || needsHttp2(userSource) || wantWeb || wantRpc;
        boolean wantHttp2Stack = needsHttp2(userSource) || wantHttpStack;
        boolean wantTlsStack = needsTls(userSource) || wantHttpStack;
        if (wantTlsStack) {
            resources.add("stdlib/tls/TlsLib.java");
        }
        if (wantHttpStack) {
            resources.add("stdlib/http/HttpLib.java");
        }
        if (wantHttp2Stack) {
            resources.add("stdlib/http/Http2Lib.java");
        }
        if (wantWeb || wantRpc) {
            resources.add("stdlib/web/WebLib.java");
            resources.add("stdlib/web/RealtimeLib.java");
            resources.add("stdlib/web/AuthLib.java");
        }
        if (wantBrowser || needsBrowser(userSource)) {
            resources.add("stdlib/browser/BrowserLib.java");
        }
        if (needsWebui(userSource)) {
            resources.add("stdlib/webui/WebUiLib.java");
        }
        if (wantRpc || needsRpc(userSource)) {
            resources.add("stdlib/rpc/RpcLib.java");
        }
        List<SourceUnit> out = new ArrayList<>();
        for (String res : resources) {
            out.add(new SourceUnit("<stdlib>/" + res, load(res)));
        }
        return out;
    }

    /** Legacy: concatenate stdlibs in front of user source (IDE remap / transitional). */
    public static String prependStdlibs(String userSource) throws IOException {
        List<SourceUnit> libs = collectStdlibSourceUnits(userSource);
        if (libs.isEmpty()) {
            return userSource;
        }
        StringBuilder sb = new StringBuilder();
        for (SourceUnit u : libs) {
            sb.append(u.sourceText()).append("\n\n");
        }
        sb.append(userSource);
        return sb.toString();
    }

    public static String prependBrowserStdlib(String userSource) throws IOException {
        return load("stdlib/browser/BrowserLib.java") + "\n\n" + userSource;
    }

    public static String prependWebuiStdlib(String userSource) throws IOException {
        if (userSource.contains("// JNative WebUI (0.27)")) {
            return userSource;
        }
        return load("stdlib/webui/WebUiLib.java") + "\n\n" + userSource;
    }

    private static String load(String resource) throws IOException {
        try (InputStream in = StdlibIncluder.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("missing stdlib resource: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Prefer `public class App`, else last top-level `public class` in the user unit. */
    static int indexOfPrimaryPublicClass(String mergedSource) {
        if (mergedSource == null) {
            return -1;
        }
        int app = mergedSource.lastIndexOf("public class App");
        if (app > 0) {
            return app;
        }
        int idx = mergedSource.lastIndexOf("public class ");
        return idx;
    }
}
