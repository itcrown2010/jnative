/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.e2e;

import io.jnative.compiler.CompileResult;
import io.jnative.compiler.CompilerOptions;
import io.jnative.compiler.JNativeCompiler;
import io.jnative.compiler.ProgramSources;
import io.jnative.compiler.SourceUnit;
import io.jnative.backend.Toolchain;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UI int geometry and graphics float geometry must coexist when both modules load.
 *
 * @author 张晓达
 */
public class GeomModulesComboEndToEndTest {

    @TempDir
    Path tmp;

    @Test
    void uiAloneCompiles() throws Exception {
        compileOk("""
                import jnative.ui.*;
                public class UiOnly {
                    public static void main(String[] args) {
                        Point p = new Point(1, 2);
                        Size s = new Size(3, 4);
                        Rect r = new Rect(0, 0, s.width, s.height);
                        System.out.println(p.x + r.width);
                    }
                }
                """, "ui-only");
    }

    @Test
    void graphicsAloneCompiles() throws Exception {
        compileOk("""
                import jnative.graphics.*;
                public class GfxOnly {
                    public static void main(String[] args) {
                        PointF p = new PointF(1.5, 2.5);
                        SizeF s = new SizeF(3.0, 4.0);
                        RectF r = new RectF(0.0, 0.0, s.width, s.height);
                        Image img = Image.create(8, 8);
                        Graphics g = img.beginGraphics();
                        Size m = g.measureText("hi", null);
                        g.end();
                        img.dispose();
                        System.out.println((int) p.x + m.width + (int) r.width);
                    }
                }
                """, "gfx-only");
    }

    @Test
    void uiPlusGraphicsCompiles() throws Exception {
        compileOk("""
                import jnative.ui.*;
                import jnative.graphics.*;
                public class Both {
                    public static void main(String[] args) {
                        Point ip = new Point(1, 2);
                        PointF fp = new PointF(1.5, 2.5);
                        Image img = Image.create(4, 4);
                        Graphics g = img.beginGraphics();
                        Size m = g.measureText("x", null);
                        g.end();
                        img.dispose();
                        System.out.println(ip.x + (int) fp.x + m.width);
                    }
                }
                """, "both");
    }

    private void compileOk(String src, String name) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        Path file = tmp.resolve(name + ".java");
        Files.writeString(file, src, StandardCharsets.UTF_8);
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(file);
        options.setProgramSources(ProgramSources.of(SourceUnit.ofPath(file, src)));
        options.setOutputFile(tmp.resolve(name + "-out"));
        CompileResult cr = new JNativeCompiler().compile(options);
        assertTrue(cr.success(), () -> cr.diagnostics().stream().map(Object::toString)
                .reduce("", (a, b) -> a + "\n" + b));
    }
}
