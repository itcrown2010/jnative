package io.jnative.e2e;

import io.jnative.backend.Toolchain;
import io.jnative.compiler.CompileResult;
import io.jnative.compiler.CompilerOptions;
import io.jnative.compiler.JNativeCompiler;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** JNative 0.18 advanced UI controls — headless e2e. */
class UiAdvancedEndToEndTest {

    @TempDir
    Path tempDir;

    @Test
    void checkBoxToggle() throws Exception {
        assertEquals("1\n0\n", run("""
                import jnative.ui.*;
                public class Main {
                    public static void main(String[] args) {
                        Application.setHeadless(1);
                        Application app = new Application();
                        Window window = new Window("T", 200, 100);
                        CheckBox box = new CheckBox("Enable");
                        window.setContent(box);
                        window.show();
                        UiTest.toggle(box);
                        if (box.isSelected()) {
                            System.out.println(1);
                        } else {
                            System.out.println(0);
                        }
                        UiTest.toggle(box);
                        if (box.isSelected()) {
                            System.out.println(1);
                        } else {
                            System.out.println(0);
                        }
                        UiTest.closeWindow(window);
                        app.run();
                    }
                }
                """, "UiCheckBox"));
    }

    @Test
    void radioToggleGroup() throws Exception {
        assertEquals("1\n0\n1\n", run("""
                import jnative.ui.*;
                public class Main {
                    public static void main(String[] args) {
                        Application.setHeadless(1);
                        Application app = new Application();
                        Window window = new Window("T", 200, 100);
                        RadioButton a = new RadioButton("A");
                        RadioButton b = new RadioButton("B");
                        ToggleGroup g = new ToggleGroup();
                        g.add(a);
                        g.add(b);
                        a.setSelected(true);
                        if (a.isSelected()) {
                            System.out.println(1);
                        } else {
                            System.out.println(0);
                        }
                        b.setSelected(true);
                        if (a.isSelected()) {
                            System.out.println(1);
                        } else {
                            System.out.println(0);
                        }
                        if (b.isSelected()) {
                            System.out.println(1);
                        } else {
                            System.out.println(0);
                        }
                        window.setContent(a);
                        window.show();
                        UiTest.closeWindow(window);
                        app.run();
                    }
                }
                """, "UiRadio"));
    }

    @Test
    void comboBoxSelection() throws Exception {
        assertEquals("1\nJNative\n", run("""
                import jnative.ui.*;
                public class Main {
                    public static void main(String[] args) {
                        Application.setHeadless(1);
                        Application app = new Application();
                        Window window = new Window("T", 200, 100);
                        ComboBox<String> combo = new ComboBox<String>();
                        combo.setRenderer(s -> s);
                        combo.addItem("Java");
                        combo.addItem("JNative");
                        int[] fired = new int[1];
                        combo.onSelectionChanged(ev -> {
                            fired[0] = fired[0] + 1;
                        });
                        combo.setSelectedIndex(1);
                        System.out.println(fired[0]);
                        System.out.println(combo.getSelectedItem());
                        window.setContent(combo);
                        window.show();
                        UiTest.closeWindow(window);
                        app.run();
                    }
                }
                """, "UiCombo"));
    }

    @Test
    void textAreaAndProgress() throws Exception {
        assertEquals("hello\n50\n", run("""
                import jnative.ui.*;
                public class Main {
                    public static void main(String[] args) {
                        Application.setHeadless(1);
                        Application app = new Application();
                        Window window = new Window("T", 300, 200);
                        TextArea area = new TextArea();
                        area.setText("hello");
                        ProgressBar bar = new ProgressBar();
                        bar.setProgress(0.5);
                        VBox root = new VBox();
                        root.add(area);
                        root.add(bar);
                        window.setContent(root);
                        window.show();
                        System.out.println(area.getText());
                        double p = bar.getProgress();
                        if (p > 0.49) {
                            if (p < 0.51) {
                                System.out.println(50);
                            } else {
                                System.out.println(0);
                            }
                        } else {
                            System.out.println(0);
                        }
                        UiTest.closeWindow(window);
                        app.run();
                    }
                }
                """, "UiTextProgress"));
    }

    @Test
    void listViewVirtualizedSelection() throws Exception {
        assertEquals("10\nx10\n0\n", run("""
                import jnative.ui.*;
                public class Main {
                    public static void main(String[] args) {
                        Application.setHeadless(1);
                        Application app = new Application();
                        Window window = new Window("T", 300, 200);
                        ListView<String> list = new ListView<String>();
                        list.setRenderer(s -> s);
                        ArrayList<String> items = new ArrayList<String>();
                        int i = 0;
                        while (i < 100) {
                            items.add("x" + i);
                            i = i + 1;
                        }
                        list.setItems(items);
                        int[] fired = new int[1];
                        list.onSelectionChanged(ev -> {
                            fired[0] = fired[0] + 1;
                        });
                        list.select(10);
                        System.out.println(list.getSelectionModel().getSelectedIndex());
                        System.out.println(list.getSelectionModel().getSelectedItem());
                        System.out.println(list.managedRowComponentCount());
                        window.setContent(list);
                        window.show();
                        UiTest.closeWindow(window);
                        app.run();
                    }
                }
                """, "UiListView"));
    }

    @Test
    void tableViewColumnsAndSort() throws Exception {
        assertEquals("2\n0\n", run("""
                import jnative.ui.*;
                class Person {
                    String name;
                    int age;
                    Person(String n, int a) {
                        name = n;
                        age = a;
                    }
                }
                class NameCol implements ColumnRenderer<Person> {
                    String getTitle() { return "Name"; }
                    String renderRow(Person p) { return p.name; }
                }
                class AgeCol implements ColumnRenderer<Person> {
                    String getTitle() { return "Age"; }
                    String renderRow(Person p) { return "age"; }
                }
                public class Main {
                    public static void main(String[] args) {
                        Application.setHeadless(1);
                        Application app = new Application();
                        Window window = new Window("T", 400, 300);
                        TableView<Person> table = new TableView<Person>();
                        table.addColumn(new NameCol());
                        table.addColumn(new AgeCol());
                        ArrayList<Person> people = new ArrayList<Person>();
                        people.add(new Person("Bob", 30));
                        people.add(new Person("Alice", 20));
                        table.setItems(people);
                        table.sortByColumn(0);
                        table.select(0);
                        System.out.println(table.modelSize());
                        System.out.println(table.managedRowComponentCount());
                        window.setContent(table);
                        window.show();
                        UiTest.closeWindow(window);
                        app.run();
                    }
                }
                """, "UiTableView"));
    }

    @Test
    void treeViewExpandLazy() throws Exception {
        assertEquals("1\n3\n1\n", run("""
                import jnative.ui.*;
                class Node {
                    String name;
                    ArrayList<Node> kids;
                    Node(String n) {
                        name = n;
                        kids = new ArrayList<Node>();
                    }
                }
                class Model implements TreeModel<Node> {
                    Node r;
                    Model(Node root) { r = root; }
                    Node root() { return r; }
                    int childCount(Node node) { return node.kids.size(); }
                    Node child(Node node, int index) { return node.kids.get(index); }
                }
                public class Main {
                    public static void main(String[] args) {
                        Application.setHeadless(1);
                        Application app = new Application();
                        Window window = new Window("T", 300, 300);
                        Node root = new Node("root");
                        root.kids.add(new Node("a"));
                        root.kids.add(new Node("b"));
                        TreeView<Node> tree = new TreeView<Node>();
                        tree.setRenderer(n -> n.name);
                        tree.setModel(new Model(root));
                        System.out.println(tree.flatSize());
                        tree.expand(0);
                        System.out.println(tree.flatSize());
                        if (tree.isExpanded(0)) {
                            System.out.println(1);
                        } else {
                            System.out.println(0);
                        }
                        window.setContent(tree);
                        window.show();
                        UiTest.closeWindow(window);
                        app.run();
                    }
                }
                """, "UiTreeView"));
    }

    @Test
    void menuAction() throws Exception {
        assertEquals("1\n", run("""
                import jnative.ui.*;
                public class Main {
                    public static void main(String[] args) {
                        Application.setHeadless(1);
                        Application app = new Application();
                        Window window = new Window("T", 300, 200);
                        MenuBar bar = new MenuBar();
                        Menu file = new Menu("File");
                        int[] fired = new int[1];
                        MenuItem open = new MenuItem("Open", () -> {
                            fired[0] = fired[0] + 1;
                        });
                        file.add(open);
                        bar.add(file);
                        window.setMenuBar(bar);
                        window.show();
                        UiTest.action(open);
                        System.out.println(fired[0]);
                        UiTest.closeWindow(window);
                        app.run();
                    }
                }
                """, "UiMenu"));
    }

    @Test
    void dialogsAndFileDialog() throws Exception {
        assertEquals("1\n/tmp/demo.java\n", run("""
                import jnative.ui.*;
                public class Main {
                    public static void main(String[] args) {
                        Application.setHeadless(1);
                        Application app = new Application();
                        Window window = new Window("T", 300, 200);
                        window.show();
                        UiTest.setDialogResult(1);
                        boolean ok = Dialogs.confirm(window, "Delete?");
                        if (ok) {
                            System.out.println(1);
                        } else {
                            System.out.println(0);
                        }
                        UiTest.setFileResult("/tmp/demo.java");
                        String path = FileDialog.open(window, "*.java");
                        System.out.println(path);
                        UiTest.closeWindow(window);
                        app.run();
                    }
                }
                """, "UiDialog"));
    }

    @Test
    void tabSplitGrid() throws Exception {
        assertEquals("1\n", run("""
                import jnative.ui.*;
                public class Main {
                    public static void main(String[] args) {
                        Application.setHeadless(1);
                        Application app = new Application();
                        Window window = new Window("T", 600, 400);
                        TabPane tabs = new TabPane();
                        tabs.addTab("A", new Label("a"));
                        tabs.addTab("B", new Label("b"));
                        tabs.setSelectedIndex(1);
                        SplitPane split = new SplitPane(Orientation.HORIZONTAL);
                        split.setDividerPosition(0.3);
                        split.setFirst(new Label("L"));
                        split.setSecond(tabs);
                        GridPane grid = new GridPane();
                        grid.addAt(new Label("Name"), 0, 0);
                        grid.addAt(new TextField(), 1, 0);
                        VBox root = new VBox();
                        root.add(split);
                        root.add(grid);
                        window.setContent(root);
                        window.show();
                        System.out.println(tabs.getSelectedIndex());
                        UiTest.closeWindow(window);
                        app.run();
                    }
                }
                """, "UiLayout"));
    }

    @Test
    void keyMouseFocusEvents() throws Exception {
        assertEquals("1\n1\n1\n", run("""
                import jnative.ui.*;
                public class Main {
                    public static void main(String[] args) {
                        Application.setHeadless(1);
                        Application app = new Application();
                        Window window = new Window("T", 200, 100);
                        Button b = new Button("X");
                        int[] k = new int[1];
                        int[] m = new int[1];
                        int[] f = new int[1];
                        b.onKeyDown(ev -> { k[0] = 1; });
                        b.onMouse(ev -> { m[0] = 1; });
                        b.onFocus(ev -> { f[0] = 1; });
                        window.setContent(b);
                        window.show();
                        UiTest.keyPress(b, KeyCode.ENTER);
                        UiTest.mouseClick(b, 5, 5);
                        UiTest.focus(b);
                        System.out.println(k[0]);
                        System.out.println(m[0]);
                        System.out.println(f[0]);
                        UiTest.closeWindow(window);
                        app.run();
                    }
                }
                """, "UiEvents"));
    }

    @Test
    void toolbarAndBreadcrumbBar() throws Exception {
        assertEquals("1\n4\n", run("""
                import jnative.ui.*;
                class Nav implements BreadcrumbNavigate {
                    static String last;
                    void go(String path) { last = path; }
                }
                public class Main {
                    public static void main(String[] args) {
                        Application.setHeadless(1);
                        Application app = new Application();
                        Window window = new Window("T", 640, 400);
                        Toolbar tb = new Toolbar();
                        Button b = tb.addToolButton("←", () -> { });
                        BreadcrumbBar crumbs = new BreadcrumbBar();
                        crumbs.setOnNavigate(new Nav());
                        crumbs.setPath("/Users/demo/Documents");
                        VBox root = new VBox();
                        root.add(tb);
                        root.add(crumbs);
                        window.setContent(root);
                        window.show();
                        System.out.println(b.chromeStyle);
                        System.out.println(crumbs.segments.size());
                        UiTest.closeWindow(window);
                        app.run();
                    }
                }
                """, "UiToolbarBreadcrumb"));
    }

    @Test
    void millionListNoRowComponents() throws Exception {
        assertEquals("0\n1000000\n", run("""
                import jnative.ui.*;
                class BigModel implements ListModel<String> {
                    int size() { return 1000000; }
                    String get(int index) { return "r"; }
                }
                public class Main {
                    public static void main(String[] args) {
                        Application.setHeadless(1);
                        Application app = new Application();
                        Window window = new Window("T", 300, 200);
                        ListView<String> list = new ListView<String>();
                        list.setRenderer(s -> s);
                        list.setModel(new BigModel());
                        System.out.println(list.managedRowComponentCount());
                        System.out.println(list.modelSize());
                        window.setContent(list);
                        window.show();
                        UiTest.closeWindow(window);
                        app.run();
                    }
                }
                """, "UiListMillion"));
    }

    @Test
    void backgroundThreadUpdatesTable() throws Exception {
        assertEquals("done\n", run("""
                import jnative.ui.*;
                class StrCol implements ColumnRenderer<String> {
                    String getTitle() { return "V"; }
                    String renderRow(String s) { return s; }
                }
                class Holder {
                    static Label status;
                    static TableView<String> table;
                    static ArrayList<String> rows;
                }
                public class Main {
                    public static void main(String[] args) {
                        Application.setHeadless(1);
                        Application app = new Application();
                        Window window = new Window("T", 400, 300);
                        Holder.status = new Label("Loading...");
                        Holder.table = new TableView<String>();
                        Holder.table.addColumn(new StrCol());
                        VBox root = new VBox();
                        root.add(Holder.status);
                        root.add(Holder.table);
                        window.setContent(root);
                        window.show();
                        Thread worker = new Thread(() -> {
                            Holder.rows = new ArrayList<String>();
                            Holder.rows.add("a");
                            Holder.rows.add("b");
                            Application.invokeLater(() -> {
                                Holder.table.setItems(Holder.rows);
                                Holder.status.setText("done");
                                System.out.println(Holder.status.getText());
                                Application.quit();
                            });
                        });
                        worker.start();
                        app.run();
                        worker.join();
                    }
                }
                """, "UiBgThread"));
    }

    private String run(String source, String name) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        // Avoid ternary in sources that still use ? : for println helpers — rewrite failing ones later if needed.
        Path javaFile = tempDir.resolve(name + ".java");
        Files.writeString(javaFile, source, StandardCharsets.UTF_8);
        Path out = tempDir.resolve(name);
        CompilerOptions options = new CompilerOptions();
        options.setOptLevel(0);
        options.setSourceFile(javaFile);
        options.setOutputFile(out);
        options.setHeadless(true);
        options.setUiBackend("headless");
        CompileResult result = new JNativeCompiler().compile(options);
        assertTrue(result.success(), () -> result.diagnostics().toString());

        ProcessBuilder pb = new ProcessBuilder(out.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        Map<String, String> e = new HashMap<>();
        e.put("JNATIVE_HEADLESS", "1");
        pb.environment().putAll(e);
        Process process = pb.start();
        byte[] bytes = process.getInputStream().readAllBytes();
        assertTrue(process.waitFor(180, TimeUnit.SECONDS), "timeout");
        String output = new String(bytes, StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), output);
        return output;
    }
}
