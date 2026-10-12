/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.mobile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Structured process launcher (no shell string concatenation).
 * @author 张晓达
 */
public final class MobileProcess {

    public record Result(int exitCode, String output, boolean timedOut) {
        public boolean ok() {
            return !timedOut && exitCode == 0;
        }
    }

    private MobileProcess() {
    }

    public static Result run(List<String> command, Path cwd, Map<String, String> env, long timeoutSec)
            throws Exception {
        ProcessBuilder pb = new ProcessBuilder(new ArrayList<>(command));
        if (cwd != null) {
            pb.directory(cwd.toFile());
        }
        if (env != null) {
            pb.environment().putAll(env);
        }
        pb.redirectErrorStream(true);
        Process p = pb.start();
        StringBuilder sb = new StringBuilder();
        Thread reader = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line).append('\n');
                    System.out.println(line);
                }
            } catch (Exception ignored) {
            }
        }, "jnative-mobile-proc");
        reader.setDaemon(true);
        reader.start();
        boolean finished = p.waitFor(timeoutSec, TimeUnit.SECONDS);
        if (!finished) {
            p.destroyForcibly();
            reader.join(2000);
            return new Result(-1, sb.toString(), true);
        }
        reader.join(5000);
        return new Result(p.exitValue(), sb.toString(), false);
    }
}
