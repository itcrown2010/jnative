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
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Round 2 — Time minimum profile native e2e. */
class TimeEndToEndTest {

    @TempDir
    Path tempDir;

    @Test
    void instantDurationClock() throws Exception {
        assertEquals("1000\n500\n1\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        Instant a = Instant.ofEpochMilli(1000L);
                        Instant b = a.plus(Duration.ofMillis(500L));
                        System.out.println(a.toEpochMilli());
                        System.out.println(Duration.between(a, b).toMillis());
                        Clock fixed = Clock.fixed(a);
                        if (fixed.instant().equals(a)) {
                            System.out.println(1);
                        } else {
                            System.out.println(0);
                        }
                    }
                }
                """, "TimeInst", 0));
    }

    @Test
    void localDateLeapAndArithmetic() throws Exception {
        // Jan 31 + 1 month → Feb 29 (end-of-month clamp, Java-compatible)
        assertEquals("0\n1\n1\n2024-02-29\n2024-02-29\n2025-01-01\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        LocalDate y1900 = LocalDate.of(1900, 2, 28);
                        if (y1900.isLeapYear()) {
                            System.out.println(1);
                        } else {
                            System.out.println(0);
                        }
                        LocalDate y2000 = LocalDate.of(2000, 1, 1);
                        if (y2000.isLeapYear()) {
                            System.out.println(1);
                        } else {
                            System.out.println(0);
                        }
                        LocalDate y2024 = LocalDate.of(2024, 2, 28);
                        if (y2024.isLeapYear()) {
                            System.out.println(1);
                        } else {
                            System.out.println(0);
                        }
                        System.out.println(y2024.plusDays(1L).toString());
                        LocalDate jan31 = LocalDate.of(2024, 1, 31);
                        System.out.println(jan31.plusMonths(1L).toString());
                        LocalDate dec31 = LocalDate.of(2024, 12, 31);
                        System.out.println(dec31.plusDays(1L).toString());
                    }
                }
                """, "TimeDate", 0));
    }

    @Test
    void localTimeMidnightWrap() throws Exception {
        assertEquals("01:00:00\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        LocalTime t = LocalTime.of(23, 0, 0);
                        System.out.println(t.plusHours(2L).toString());
                    }
                }
                """, "TimeTod", 0));
    }

    @Test
    void zoneOffsetAndShanghai() throws Exception {
        assertEquals("Z\n+08:00\n2024-01-01T08:00:00\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        System.out.println(ZoneOffset.UTC().toString());
                        System.out.println(ZoneOffset.ofHours(8).toString());
                        Instant i = Instant.ofEpochMilli(1704067200000L);
                        ZonedDateTime z = ZonedDateTime.ofInstant(i, ZoneId.of("Asia/Shanghai"));
                        System.out.println(z.toLocalDateTime().toString());
                    }
                }
                """, "TimeZone", 0));
    }

    @Test
    void losAngelesDstSpringForward() throws Exception {
        // 2024-03-10 09:00 UTC = 01:00 PST (UTC-8); 11:00 UTC = 04:00 PDT (UTC-7).
        assertEquals("1\n1\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        ZoneId la = ZoneId.of("America/Los_Angeles");
                        int before = la.getTotalOffsetMinutes(1710061200000L);
                        int after = la.getTotalOffsetMinutes(1710068400000L);
                        if (before == 0 - 480) {
                            System.out.println(1);
                        } else {
                            System.out.println(before);
                        }
                        if (after == 0 - 420) {
                            System.out.println(1);
                        } else {
                            System.out.println(after);
                        }
                    }
                }
                """, "TimeDst", 0));
    }

    @Test
    void timeNativeE2E_O0_O2_O3() throws Exception {
        String src = """
                public class Main {
                    public static void main(String[] args) {
                        LocalDate d = LocalDate.of(2024, 2, 29);
                        Instant i = Instant.ofEpochSecond(1700000000L);
                        Duration dur = Duration.ofHours(2L);
                        ZoneId sh = ZoneId.of("Asia/Shanghai");
                        ZoneId la = ZoneId.of("America/Los_Angeles");
                        ZoneId ld = ZoneId.of("Europe/London");
                        int ok = 0;
                        if (d.isLeapYear()) {
                            if (i.plus(dur).toEpochMilli() == i.toEpochMilli() + 7200000L) {
                                if (sh.getTotalOffsetMinutes(i.toEpochMilli()) == 480) {
                                    int laOff = la.getTotalOffsetMinutes(i.toEpochMilli());
                                    int ldOff = ld.getTotalOffsetMinutes(i.toEpochMilli());
                                    if (laOff < 0) {
                                        if (ldOff == 0 || ldOff == 60) {
                                            ok = 1;
                                        }
                                    }
                                }
                            }
                        }
                        if (ok == 1) {
                            System.out.println("JNATIVE_TIME_PASS");
                        }
                    }
                }
                """;
        assertEquals("JNATIVE_TIME_PASS\n", run(src, "TimeAll0", 0));
        assertEquals("JNATIVE_TIME_PASS\n", run(src, "TimeAll2", 2));
        assertEquals("JNATIVE_TIME_PASS\n", run(src, "TimeAll3", 3));
    }

    private String run(String source, String name, int opt) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        Path javaFile = tempDir.resolve(name + ".java");
        Files.writeString(javaFile, source, StandardCharsets.UTF_8);
        Path out = tempDir.resolve(name);
        CompilerOptions options = new CompilerOptions();
        options.setOptLevel(opt);
        options.setSourceFile(javaFile);
        options.setOutputFile(out);
        CompileResult result = new JNativeCompiler().compile(options);
        assertTrue(result.success(), () -> result.diagnostics().toString());
        ProcessBuilder pb = new ProcessBuilder(out.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        byte[] bytes = process.getInputStream().readAllBytes();
        assertTrue(process.waitFor(120, TimeUnit.SECONDS), "timeout");
        String output = new String(bytes, StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), output);
        return output;
    }
}
