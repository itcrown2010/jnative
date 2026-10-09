package io.jnative.format;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author 张晓达
 */
class SourceFormatterTest {

    @Test
    void idempotentAndPreservesStringAndComment() {
        String src = """
                public class Main{public static void main(String[] args){
                int x=1+2;// keep
                System.out.println("a  b");
                }}
                """;
        SourceFormatter f = new SourceFormatter();
        var r = f.formatDocument("Main.java", src);
        assertTrue(r.ok(), () -> String.valueOf(r.error()));
        assertTrue(r.text().contains("\"a  b\""));
        assertTrue(r.text().contains("// keep"));
        var r2 = f.formatDocument("Main.java", r.text());
        assertTrue(r2.ok(), () -> String.valueOf(r2.error()));
        assertEquals(r.text(), r2.text());
    }

    @Test
    void preservesCrlf() {
        String src = "public class Main {\r\n"
                + "    public static void main(String[] args) {\r\n"
                + "        int x = 1;\r\n"
                + "    }\r\n"
                + "}\r\n";
        var r = new SourceFormatter().formatDocument("Main.java", src);
        assertTrue(r.ok(), () -> String.valueOf(r.error()));
        assertTrue(r.text().contains("\r\n"));
    }

    @Test
    void rejectsBrokenSyntax() {
        var r = new SourceFormatter().formatDocument("Main.java", "public class Main { int x = ; }");
        assertFalse(r.ok());
    }
}
