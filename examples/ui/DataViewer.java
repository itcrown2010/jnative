import jnative.ui.*;

/** 100k-row TableView demo (virtualized — zero row Components). */
class Row {
    String name;
    int id;

    Row(String n, int i) {
        name = n;
        id = i;
    }
}

class BigRows implements ListModel<Row> {
    int size() {
        return 100000;
    }

    Row get(int index) {
        return new Row("row", index);
    }
}

class NameCol implements ColumnRenderer<Row> {
    String getTitle() {
        return "Name";
    }

    String renderRow(Row r) {
        return r.name;
    }
}

class IdCol implements ColumnRenderer<Row> {
    String getTitle() {
        return "Id";
    }

    String renderRow(Row r) {
        return "id";
    }
}

public class DataViewer {
    public static void main(String[] args) {
        Application.setHeadless(1);
        Application app = new Application();
        Window window = new Window("People", 1000, 700);
        TableView<Row> table = new TableView<Row>();
        table.addColumn(new NameCol());
        table.addColumn(new IdCol());
        table.setModel(new BigRows());
        System.out.println(table.modelSize());
        System.out.println(table.managedRowComponentCount());
        window.setContent(table);
        window.show();
        UiTest.closeWindow(window);
        app.run();
    }
}
