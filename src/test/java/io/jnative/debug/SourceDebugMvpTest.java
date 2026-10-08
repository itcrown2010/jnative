package io.jnative.debug;

import io.jnative.backend.Toolchain;
import io.jnative.cli.CliArguments;
import io.jnative.compiler.CompilerOptions;
import io.jnative.compiler.CompileResult;
import io.jnative.compiler.JNativeCompiler;
import io.jnative.ir.SourceLocationMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class SourceDebugMvpTest {

    @TempDir
    Path tmp;

    @Test
    void debugDefaultsToO0UnlessOptExplicit() {
        CompilerOptions a = CliArguments.parse(new String[]{"--debug", "X.java"});
        assertTrue(a.emitSourceDebugInfo());
        assertEquals(0, a.optLevel());
        assertFalse(a.optLevelExplicit());

        CompilerOptions b = CliArguments.parse(new String[]{"-O2", "--debug", "X.java"});
        assertTrue(b.emitSourceDebugInfo());
        assertEquals(2, b.optLevel());
        assertTrue(b.optLevelExplicit());

        CompilerOptions c = CliArguments.parse(new String[]{"--debug", "-O1", "X.java"});
        assertTrue(c.emitSourceDebugInfo());
        assertEquals(1, c.optLevel());
        assertTrue(c.optLevelExplicit());
    }

    @Test
    void ordinaryBuildDoesNotEmitSourceDebugInfo() {
        CompilerOptions o = CliArguments.parse(new String[]{"X.java"});
        assertFalse(o.emitSourceDebugInfo());
    }

    @Test
    void stdlibLineOffsetMapsUserLines() throws Exception {
        String user = "class U {\n  int x;\n}\n";
        String prepended = "class Lib {\n  void f() {}\n}\n\n" + user;
        SourceLocationMapper m = SourceLocationMapper.forPrepended("/tmp/U.java", prepended, user);
        assertTrue(m.lineOffset() > 0);
        var mapped = m.map(new io.jnative.diagnostics.SourceLocation("U.java", m.lineOffset() + 2, 3));
        assertEquals("/tmp/U.java", mapped.file());
        assertEquals(2, mapped.line());
        var std = m.map(new io.jnative.diagnostics.SourceLocation("U.java", 1, 1));
        assertEquals(io.jnative.diagnostics.SourceLocation.UNKNOWN, std);
    }

    @Test
    void debugCompileEmitsDiAndRuns() throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        Path src = tmp.resolve("DebugDemo.java");
        Files.writeString(src, """
                class DebugDemo {
                    static int add(int a, int b) {
                        int result = a + b;
                        return result;
                    }
                    public static void main(String[] args) {
                        int x = 10;
                        int y = 20;
                        int sum = add(x, y);
                        System.out.println(sum);
                    }
                }
                """, StandardCharsets.UTF_8);
        Path out = tmp.resolve("DebugDemo");

        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(src);
        options.setOutputFile(out);
        options.setEmitSourceDebugInfo(true);
        options.setDebugSymbols(true);
        options.setOptLevel(0);
        // Do not set emitLlvm — that triggers emit-only and skips linking.

        CompileResult result = new JNativeCompiler().compile(options);
        assertTrue(result.success(), () -> result.diagnostics().toString());
        assertNotNull(result.executable());
        assertTrue(Files.isRegularFile(result.executable()));
        assertNotNull(result.llvmIr());
        String llvm = result.llvmIr();
        assertTrue(llvm.contains("!DICompileUnit"), "missing DICompileUnit");
        assertTrue(llvm.contains("!DIFile"), "missing DIFile");
        assertTrue(llvm.contains("DebugDemo.java") || llvm.contains(src.getFileName().toString()),
                "DIFile should reference user source");
        assertTrue(llvm.contains("!DISubprogram"), "missing DISubprogram");
        assertTrue(llvm.contains("!DILocation"), "missing DILocation");
        assertTrue(llvm.contains("!DILocalVariable"), "missing DILocalVariable");
        assertTrue(llvm.contains("llvm.dbg.declare"), "missing dbg.declare");
        assertTrue(llvm.contains("name: \"add\"") || llvm.contains("name: \"main\""),
                "expected user method display names");

        Process p = new ProcessBuilder(result.executable().toAbsolutePath().toString())
                .redirectErrorStream(true)
                .start();
        boolean finished = p.waitFor(15, TimeUnit.SECONDS);
        if (!finished) {
            p.destroyForcibly();
            fail("timeout");
        }
        String stdout = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p.exitValue(), stdout);
        assertEquals("30\n", stdout);
    }

    @Test
    void concatenatedLineMapMapsToOriginalFiles() throws Exception {
        Path a = tmp.resolve("A.java");
        Path b = tmp.resolve("B.java");
        Files.writeString(a, "class A {\n  static int f() { return 1; }\n}\n");
        Files.writeString(b, "class B {\n  public static void main(String[] args) {\n    int x = A.f();\n  }\n}\n");
        String unit = Files.readString(a) + "\n" + Files.readString(b);
        int aLines = 3; // class A { / static... / }
        io.jnative.ir.SourceLineMap map = new io.jnative.ir.SourceLineMap(java.util.List.of(
                new io.jnative.ir.SourceLineMap.Segment(1, aLines + 1, a.toAbsolutePath().toString(), 1),
                new io.jnative.ir.SourceLineMap.Segment(aLines + 2, aLines + 2 + 4, b.toAbsolutePath().toString(), 1)
        ));
        Path mapPath = tmp.resolve("unit.java.linemap");
        map.write(mapPath);
        io.jnative.ir.SourceLineMap loaded = io.jnative.ir.SourceLineMap.read(mapPath);
        var locA = loaded.mapUserLine(2, 1);
        assertTrue(locA.file().endsWith("A.java"));
        assertEquals(2, locA.line());
        var locB = loaded.mapUserLine(aLines + 3, 1);
        assertTrue(locB.file().endsWith("B.java"));

        String prepended = "class Lib {}\n\n" + unit;
        var mapper = SourceLocationMapper.forPrepended(
                tmp.resolve("unit.java").toString(), prepended, unit, loaded);
        int offset = mapper.lineOffset();
        var mapped = mapper.map(new io.jnative.diagnostics.SourceLocation("unit.java", offset + 2, 3));
        assertTrue(mapped.file().endsWith("A.java"));
        assertEquals(2, mapped.line());
        assertEquals(io.jnative.diagnostics.SourceLocation.UNKNOWN,
                mapper.map(new io.jnative.diagnostics.SourceLocation("unit.java", 1, 1)));
    }

    @Test
    void releaseBuildHasNoDiCompileUnit() throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        Path src = tmp.resolve("Hello.java");
        Files.writeString(src, """
                class Hello {
                    public static void main(String[] args) {
                        System.out.println(1);
                    }
                }
                """, StandardCharsets.UTF_8);
        Path out = tmp.resolve("Hello");
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(src);
        options.setOutputFile(out);
        options.setOptLevel(1);
        // emitSourceDebugInfo defaults false
        CompileResult result = new JNativeCompiler().compile(options);
        assertTrue(result.success(), () -> result.diagnostics().toString());
        assertNotNull(result.llvmIr());
        assertFalse(result.llvmIr().contains("!DICompileUnit"),
                "ordinary build must not emit source DI metadata");
    }
}
