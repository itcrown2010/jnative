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

        System.out.println(fails == 0 ? "ALL OK" : "FAILED " + fails);
        return fails;
    }
}
