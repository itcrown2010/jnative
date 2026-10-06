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
                || sourceText.contains("HttpException");
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
                || sourceText.contains("jnative.json")
                || sourceText.contains("StudioApplication")
                || sourceText.contains("jnative.studio");
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
                || sourceText.contains("CancellationToken")
                || sourceText.contains("Logger.")
                || sourceText.contains("Desktop.")
                || sourceText.contains("jnative.io")
                || sourceText.contains("StudioApplication")
                || sourceText.contains("jnative.studio");
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
        // Advanced UI / Graphics / Editor use ArrayList / Function from collections.
        if (needsCollections(text) || wantUi || wantGraphics || wantEditor || wantJson || wantIo || wantStudio) {
            text = prependCollectionsStdlib(text);
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
        if (needsHttp(text)) {
            text = prependHttpStdlib(text);
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
