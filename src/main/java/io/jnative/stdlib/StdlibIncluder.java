package io.jnative.stdlib;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Merges bundled JNative stdlib sources into a compilation unit.
 * HTTP / UI / Collections are implemented in JNative (not compiler intrinsics).
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
                || sourceText.contains("WebMiddleware")
                || sourceText.contains("WebHandler")
                || sourceText.contains("AsyncWebHandler")
                || sourceText.contains("WebException");
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
                || sourceText.contains("new CodeEditor(");
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
                || sourceText.contains("Iterable<")
                || sourceText.contains("Iterator<")
                || sourceText.contains("Comparator<")
                || sourceText.contains("Consumer<")
                || sourceText.contains("Predicate<")
                || sourceText.contains("Supplier<")
                || sourceText.contains("Function<")
                || sourceText.contains("new ArrayList")
                || sourceText.contains("new HashMap")
                || sourceText.contains("new HashSet")
                || sourceText.contains("new ArrayDeque")
                || sourceText.contains("Collections.")
                || sourceText.contains("Arrays.");
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
                || sourceText.contains("Clipboard.setText")
                || sourceText.contains("Clipboard.getText")
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

    public static boolean needsDatabase(String sourceText) {
        if (sourceText == null) {
            return false;
        }
        return sourceText.contains("jnative.db")
                || sourceText.contains("Database.connect")
                || sourceText.contains("Database.connectWithConfig")
                || sourceText.contains("DatabaseUrl")
                || sourceText.contains("RowMapper")
                || sourceText.contains("DbConnection")
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
                || sourceText.contains("Migrator")
                || sourceText.contains("PgProtocolTestKit");
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
                || needsWeb(sourceText);
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
                || sourceText.contains("CommandRegistry")
                || sourceText.contains("WorkspaceIndex")
                || sourceText.contains("CompletionService")
                || sourceText.contains("CompletionItem")
                || sourceText.contains("new CompletionItem(")
                || sourceText.contains("WorkspaceIndex")
                || sourceText.contains("StudioSymbol")
                || sourceText.contains("ProjectSearchService");
    }

    public static String prependHttpStdlib(String userSource) throws IOException {
        List<String> parts = new ArrayList<>();
        parts.add(load("stdlib/http/HttpLib.java"));
        parts.add(userSource);
        return String.join("\n\n", parts);
    }

    public static String prependWebStdlib(String userSource) throws IOException {
        List<String> parts = new ArrayList<>();
        parts.add(load("stdlib/web/WebLib.java"));
        parts.add(userSource);
        return String.join("\n\n", parts);
    }

    public static String prependUiStdlib(String userSource) throws IOException {
        List<String> parts = new ArrayList<>();
        parts.add(load("stdlib/ui/UiLib.java"));
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
        parts.add(load("stdlib/io/IoLib.java"));
        parts.add(userSource);
        return String.join("\n\n", parts);
    }

    /** Inserts DbLib + SQLite (+ optional PostgreSQL) before user source. */
    public static String insertDatabaseStdlib(String mergedSource) throws IOException {
        if (mergedSource.contains("// JNative SQLite driver (0.24)")) {
            return mergedSource;
        }
        String block = load("stdlib/db/DbLib.java")
                + "\n\n" + load("stdlib/db/SqliteDriverLib.java")
                + "\n\n" + load("stdlib/db/DbMigrateLib.java");
        if (mergedSource.contains("Instant")
                || mergedSource.contains("LocalDate")
                || mergedSource.contains("UUID.random")
                || mergedSource.contains("jnative.time")) {
            block = block + "\n\n" + load("stdlib/time/TimeLib.java");
        }
        if (mergedSource.contains("DbPgBootstrap.register")
                || mergedSource.contains("new PostgreSQLDriver")) {
            block = block + "\n\n" + load("stdlib/db/PgDriverLib.java");
        }
        int userIdx = mergedSource.lastIndexOf("public class App");
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

    public static String prependStdlibs(String userSource) throws IOException {
        String text = userSource;
        boolean wantStudio = needsStudio(userSource);
        boolean wantEditor = needsEditor(userSource) || wantStudio;
        boolean wantGraphics = needsGraphics(userSource) || wantEditor;
        boolean wantUi = needsUi(userSource) || wantGraphics || wantEditor || wantStudio;
        boolean wantJson = needsJson(userSource) || wantStudio;
        boolean wantIo = needsIo(userSource) || wantStudio;
        boolean wantWeb = needsWeb(userSource);
        boolean wantDatabase = needsDatabase(userSource);
        boolean wantAsync = needsAsync(userSource) || wantWeb || wantDatabase;
        // Advanced UI / Graphics / Editor use ArrayList / Function from collections.
        if (needsCollections(text) || wantUi || wantGraphics || wantEditor || wantJson || wantIo || wantStudio
                || wantAsync || wantDatabase) {
            text = prependCollectionsStdlib(text);
        }
        if (wantAsync) {
            text = insertAsyncStdlib(text);
        }
        if (wantDatabase) {
            text = insertDatabaseStdlib(text);
        }
        if (wantJson || needsJson(text)) {
            text = prependJsonStdlib(text);
        }
        if (wantIo || needsIo(text)) {
            text = prependIoStdlib(text);
        }
        if (wantUi || needsUi(text)) {
            text = prependUiStdlib(text);
        }
        // Graphics after UI so Canvas can extend Component.
        if (wantGraphics || needsGraphics(text)) {
            text = prependGraphicsStdlib(text);
        }
        // Editor after Graphics so CodeEditor can extend Canvas.
        if (wantEditor || needsEditor(text)) {
            text = prependEditorStdlib(text);
        }
        if (wantStudio || needsStudio(text)) {
            text = prependStudioStdlib(text);
        }
        if (needsHttp(text) || wantWeb || needsWeb(text)) {
            text = prependHttpStdlib(text);
        }
        if (wantWeb || needsWeb(text)) {
            text = prependWebStdlib(text);
        }
        return text;
    }

    private static String load(String resource) throws IOException {
        try (InputStream in = StdlibIncluder.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("missing stdlib resource: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
