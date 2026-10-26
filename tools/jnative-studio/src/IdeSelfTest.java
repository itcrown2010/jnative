import jnative.io.*;

/**
 * GUI-free checks of Studio's text tools (`studio --selftest`): re-indent, identifier rename, completion index.
 *
 * @author 张晓达
 */
public class IdeSelfTest {
    static int fails = 0;

    static void check(String name, String got, String want) {
        if (got.equals(want)) {
            System.out.println("ok   " + name);
        } else {
            fails = fails + 1;
            System.out.println("FAIL " + name + ": got [" + got + "] want [" + want + "]");
        }
    }

    static int run() {
        String src = "public class A {\nvoid f(int x) {\nif (x > 0) {\nfoo(a,\nb);\nrun(() -> {\nint y = 1;\n});\n}\n"
                + "switch (x) {\ncase 1:\nbar();\nbreak;\ndefault:\nbaz();\n}\n/* c\n* d */\nString s = \"{\" + '}';\n"
                + "String t = \"a\"\n+ \"b\";\n}\n}\n";
        String want = "public class A {\n    void f(int x) {\n        if (x > 0) {\n            foo(a,\n"
                + "                    b);\n            run(() -> {\n                int y = 1;\n            });\n"
                + "        }\n        switch (x) {\n            case 1:\n                bar();\n                break;\n"
                + "            default:\n                baz();\n        }\n        /* c\n         * d */\n"
                + "        String s = \"{\" + '}';\n        String t = \"a\"\n                + \"b\";\n    }\n}\n";
        String got = IdeFormat.reindent(src);
        check("reindent", got, want);
        check("reindent idempotent", IdeFormat.reindent(got), got);

        int[] cnt = new int[1];
        String rn = IdeRename.replaceIdent("int btn = 1; String s = \"btn\"; // btn\n x.btn(btn); char c = 'b'; int btn2;",
                "btn", "b2", cnt);
        check("rename idents", rn, "int b2 = 1; String s = \"btn\"; // btn\n x.b2(b2); char c = 'b'; int btn2;");
        check("rename count", "" + cnt[0], "3");
        String q = IdeRename.replaceQuoted("{\"name\": \"btn\", \"text\": \"btn1\"}", "btn", "x", cnt);
        check("rename quoted", q, "{\"name\": \"x\", \"text\": \"btn1\"}");

        IdeTable t = new IdeTable();
        IdeComplete.indexText("public class Foo extends Bar {\n    int size() {\n    String name;\n}\n", t, "Foo.java");
        IdeCls c = t.get("Foo");
        check("index class", c == null ? "null" : c.base + ":" + c.members.size(), "Bar:2");

        String imp = "import jnative.ui.Button;\nimport java.util.List;\nimport java.util.ArrayList;\nimport java.util.ArrayList;\n"
                + "import jnative.io.*;\nimport static java.lang.Math.max;\n\n/** List */\nclass A {\n  ArrayList<String> a; Button b = null; // List\n"
                + "  String s = \"max\";\n}\n";
        String impWant = "import java.util.ArrayList;\nimport jnative.io.*;\nimport jnative.ui.Button;\n\n/** List */\nclass A {\n"
                + "  ArrayList<String> a; Button b = null; // List\n  String s = \"max\";\n}\n";
        check("optimize imports", IdeImports.optimize(imp), impWant);
        check("optimize imports idempotent", IdeImports.optimize(impWant), impWant);
        check("no imports", IdeImports.optimize("class A {}\n"), "class A {}\n");

        check("diff same", diffStr("a\nb\nc", "a\nb\nc"), "000");
        check("diff added", diffStr("a\nc", "a\nb\nc"), "010");
        check("diff modified", diffStr("a\nb\nc", "a\nX\nc"), "020");
        check("diff deleted", diffStr("a\nb\nc", "a\nc"), "03");
        check("diff mixed", diffStr("a\nb\nc\nd\ne", "a\nB\nc\nnew\nd\ne"), "020100");
        check("diff tail", diffStr("a\nb", "a\nb\nc\nd"), "0011");
        check("diff empty base", diffStr("", "x\ny"), "11");

        System.out.println(fails == 0 ? "ALL OK" : "FAILED " + fails);
        return fails;
    }

    static String diffStr(String base, String cur) {
        ArrayList<int> m = IdeDiff.marks(DsGen.splitLines(base), DsGen.splitLines(cur));
        String r = "";
        int i = 0;
        while (i < m.size()) {
            r = r + m.get(i);
            i = i + 1;
        }
        return r;
    }
}
