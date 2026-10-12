/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.build;

/** A concrete build target (native executable, wasm browser, desktop).
 * @author 张晓达
*/
public final class BuildTargetSpec {
    private final String name;
    private final String triple; // native | wasm32-browser | desktop-native
    private final String module;
    private final String mainClass;
    private final String output;

    public BuildTargetSpec(String name, String triple, String module, String mainClass, String output) {
        this.name = name;
        this.triple = triple;
        this.module = module;
        this.mainClass = mainClass;
        this.output = output;
    }

    public String name() {
        return name;
    }

    public String triple() {
        return triple;
    }

    public String module() {
        return module;
    }

    public String mainClass() {
        return mainClass;
    }

    public String output() {
        return output;
    }

    public boolean isBrowser() {
        return "wasm32-browser".equals(triple) || "browser".equals(triple);
    }

    public boolean isNative() {
        return !isBrowser();
    }
}
