/**
 * Builds the sample form used by self-tests and the "new form" template.
 *
 * @author 张晓达
 */
public class DsSample {
    /** A form exercising every component type and container kind. */
    static DsDoc everything() {
        DsDoc d = DsDoc.blank("DemoForm");
        d.root.set("title", "演示窗体");
        d.root.set("width", "720");
        d.root.set("height", "560");
        DsNode root = d.content();
        root.set("spacing", "10");

        DsNode title = d.addChild(root, "Label", -1);
        title.set("text", "用户资料 User Profile");
        title.set("fontSize", "20");

        DsNode row = d.addChild(root, "HBox", -1);
        DsNode name = d.addChild(row, "TextField", -1);
        name.set("cueBanner", "姓名");
        name.setHandler("change", "onNameChange");
        DsNode ok = d.addChild(row, "Button", -1);
        ok.set("text", "保存");
        ok.setHandler("click", "onSaveClick");

        DsNode grid = d.addChild(root, "GridPane", -1);
        DsNode cb = d.addChild(grid, "CheckBox", -1);
        cb.set("text", "同意协议");
        cb.setHandler("toggle", "onAgreeToggle");
        DsNode rb = d.addChild(grid, "RadioButton", -1);
        rb.set("text", "男");
        DsNode combo = d.addChild(grid, "ComboBox", -1);
        combo.set("selectedIndex", "1");
        DsNode seg = d.addChild(grid, "SegmentedControl", -1);
        seg.set("items", "日\n周\n月");
        DsNode pb = d.addChild(grid, "ProgressBar", -1);
        pb.set("progress", "0.4");
        DsNode sf = d.addChild(grid, "SearchField", -1);
        sf.set("placeholder", "搜索");

        DsNode split = d.addChild(root, "SplitPane", -1);
        split.set("vgrow", "true");
        split.set("divider", "0.3");
        DsNode list = d.addChild(split, "ListView", -1);
        list.setHandler("select", "onListSelect");
        DsNode tabs = d.addChild(split, "TabPane", -1);
        DsNode area = d.addChild(tabs, "TextArea", -1);
        area.set("text", "第一行\n第二行");
        area.set("wordWrap", "true");
        DsNode panel = d.addChild(tabs, "Panel", -1);
        DsNode cv = d.addChild(panel, "Canvas", -1);
        cv.set("x", "10");
        cv.set("y", "10");
        cv.set("width", "200");
        cv.set("height", "100");
        cv.setHandler("paint", "onCanvasPaint");
        cv.setHandler("mouseDown", "onCanvasMouseDown");
        DsNode scroll = d.addChild(panel, "ScrollView", -1);
        scroll.set("x", "220");
        scroll.set("y", "10");
        scroll.set("width", "120");
        scroll.set("height", "100");
        DsNode inner = d.addChild(scroll, "Label", -1);
        inner.set("text", "scroll");
        d.root.setHandler("close", "onFormClose");
        d.selectedName = "";
        d.dirty = false;
        return d;
    }

    static DsDoc login(String cls) {
        DsDoc d = DsDoc.blank(cls);
        d.root.set("title", "登录");
        d.root.set("width", "360");
        d.root.set("height", "260");
        DsNode root = d.content();
        root.set("spacing", "12");
        root.set("padding", "24");
        DsNode t = d.addChild(root, "Label", -1);
        t.name = "lblTitle";
        t.set("text", "欢迎使用");
        t.set("fontSize", "22");
        DsNode user = d.addChild(root, "TextField", -1);
        user.name = "txtUser";
        user.set("cueBanner", "用户名");
        DsNode pw = d.addChild(root, "TextField", -1);
        pw.name = "txtPassword";
        pw.set("cueBanner", "密码");
        pw.set("password", "true");
        DsNode ok = d.addChild(root, "Button", -1);
        ok.name = "btnLogin";
        ok.set("text", "登录");
        ok.setHandler("click", "onBtnLoginClick");
        DsNode msg = d.addChild(root, "Label", -1);
        msg.name = "lblMessage";
        msg.set("text", "");
        d.select(null);
        d.dirty = false;
        return d;
    }

    static DsDoc hello(String cls) {
        DsDoc d = DsDoc.blank(cls);
        d.root.set("title", "JNative 应用");
        d.root.set("width", "420");
        d.root.set("height", "300");
        DsNode root = d.content();
        root.set("spacing", "12");
        root.set("padding", "24");
        DsNode t = d.addChild(root, "Label", -1);
        t.name = "lblTitle";
        t.set("text", "你好，JNative！");
        t.set("fontSize", "24");
        DsNode name = d.addChild(root, "TextField", -1);
        name.name = "txtName";
        name.set("cueBanner", "请输入你的名字");
        DsNode ok = d.addChild(root, "Button", -1);
        ok.name = "btnHello";
        ok.set("text", "打招呼");
        ok.setHandler("click", "onBtnHelloClick");
        DsNode msg = d.addChild(root, "Label", -1);
        msg.name = "lblMessage";
        msg.set("text", "");
        d.select(null);
        d.dirty = false;
        return d;
    }
}
