/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative;

import io.jnative.cli.CliDispatcher;

/**
 * JNative CLI entry point.
 * @author 张晓达
 */
public final class Main {
    public static final String VERSION = "0.32";

    private Main() {
    }

    public static void main(String[] args) {
        int code = run(args);
        System.exit(code);
    }

    public static int run(String[] args) {
        try {
            return CliDispatcher.dispatch(args);
        } catch (IllegalArgumentException e) {
            System.err.println("error: " + e.getMessage());
            System.err.println(CliDispatcher.helpText());
            return 1;
        } catch (Exception e) {
            System.err.println("error: " + e.getMessage());
            e.printStackTrace(System.err);
            return 1;
        }
    }
}
