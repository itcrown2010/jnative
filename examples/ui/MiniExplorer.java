import jnative.ui.*;

/**
 * Mini JNative Explorer — 0.18 IDE-shaped acceptance app.
 * Headless: compile with --headless --ui-backend headless
 */
class FileNode {
    String name;
    ArrayList<FileNode> children;

    FileNode(String n) {
        name = n;
        children = new ArrayList<FileNode>();
    }
}

class FileTreeModel implements TreeModel<FileNode> {
    FileNode r;

    FileTreeModel(FileNode root) {
        r = root;
    }

    FileNode root() {
        return r;
    }

    int childCount(FileNode node) {
        return node.children.size();
    }

    FileNode child(FileNode node, int index) {
        return node.children.get(index);
    }
}

public class MiniExplorer {
    public static void main(String[] args) {
        Application.setHeadless(1);
        Application app = new Application();
        Window window = new Window("JNative IDE", 1200, 800);

        FileNode root = new FileNode("project");
        FileNode src = new FileNode("src");
        src.children.add(new FileNode("Main.java"));
        root.children.add(src);

        TreeView<FileNode> files = new TreeView<FileNode>();
        files.setRenderer(n -> n.name);
        files.setModel(new FileTreeModel(root));
        files.expand(0);

        TextArea editor = new TextArea();
        editor.setText("// open a file via File menu");

        TabPane tabs = new TabPane();
        tabs.addTab("Editor", editor);
        tabs.addTab("Output", new Label("ready"));

        SplitPane main = new SplitPane(Orientation.HORIZONTAL);
        main.setDividerPosition(0.25);
        main.setFirst(files);
        main.setSecond(tabs);

        MenuBar menu = new MenuBar();
        Menu file = new Menu("File");
        file.add(new MenuItem("Open", () -> {
            UiTest.setFileResult("/tmp/demo.java");
            String path = FileDialog.open(window, "*.java");
            if (path != null) {
                editor.setText("// opened " + path);
            }
        }));
        file.addSeparator();
        file.add(new MenuItem("Quit", () -> Application.quit()));
        menu.add(file);

        window.setMenuBar(menu);
        window.setContent(main);
        window.show();

        UiTest.closeWindow(window);
        app.run();
    }
}
