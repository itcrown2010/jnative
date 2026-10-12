/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.pkg;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Package / project manifest model (from jnative.toml or JSON subset).
 * @author 张晓达
 */
public final class PackageManifest {
    private PackageId id = PackageId.parse("jnative/app");
    private SemanticVersion version = new SemanticVersion(0, 1, 0);
    private String description = "";
    private String license = "";
    private String repository = "";
    private String homepage = "";
    private final List<String> authors = new ArrayList<>();
    private final List<Dependency> dependencies = new ArrayList<>();
    private final List<Dependency> devDependencies = new ArrayList<>();
    private final List<Dependency> buildDependencies = new ArrayList<>();
    private final Map<String, List<Dependency>> targetDependencies = new LinkedHashMap<>();
    private final Set<String> features = new LinkedHashSet<>();
    private final Set<String> defaultFeatures = new LinkedHashSet<>();
    private final Set<TargetCapability> requires = new LinkedHashSet<>();
    private final Set<String> targets = new LinkedHashSet<>();
    private final List<NativeLibrarySpec> nativeLibraries = new ArrayList<>();
    private String minCompiler = "0.30";
    private String maxCompiler = "";
    private String minRuntime = "";
    private String packageType = "source"; // source | precompiled | resource | native | processor
    private boolean processor;
    private String processorEntry = "";
    private int minProcessorApi;
    private int maxProcessorApi;
    private final Map<String, String> permissions = new LinkedHashMap<>();

    public PackageId id() {
        return id;
    }

    public void setId(PackageId id) {
        this.id = id;
    }

    public SemanticVersion version() {
        return version;
    }

    public void setVersion(SemanticVersion version) {
        this.version = version;
    }

    public String description() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description == null ? "" : description;
    }

    public String license() {
        return license;
    }

    public void setLicense(String license) {
        this.license = license == null ? "" : license;
    }

    public String repository() {
        return repository;
    }

    public void setRepository(String repository) {
        this.repository = repository == null ? "" : repository;
    }

    public String homepage() {
        return homepage;
    }

    public void setHomepage(String homepage) {
        this.homepage = homepage == null ? "" : homepage;
    }

    public List<String> authors() {
        return authors;
    }

    public List<Dependency> dependencies() {
        return dependencies;
    }

    public List<Dependency> devDependencies() {
        return devDependencies;
    }

    public List<Dependency> buildDependencies() {
        return buildDependencies;
    }

    public Map<String, List<Dependency>> targetDependencies() {
        return targetDependencies;
    }

    public Set<String> features() {
        return features;
    }

    public Set<String> defaultFeatures() {
        return defaultFeatures;
    }

    public Set<TargetCapability> requires() {
        return requires;
    }

    public Set<String> targets() {
        return targets;
    }

    public List<NativeLibrarySpec> nativeLibraries() {
        return nativeLibraries;
    }

    public String minCompiler() {
        return minCompiler;
    }

    public void setMinCompiler(String minCompiler) {
        this.minCompiler = minCompiler == null ? "" : minCompiler;
    }

    public String maxCompiler() {
        return maxCompiler;
    }

    public void setMaxCompiler(String maxCompiler) {
        this.maxCompiler = maxCompiler == null ? "" : maxCompiler;
    }

    public String minRuntime() {
        return minRuntime;
    }

    public void setMinRuntime(String minRuntime) {
        this.minRuntime = minRuntime == null ? "" : minRuntime;
    }

    public String packageType() {
        return packageType;
    }

    public void setPackageType(String packageType) {
        this.packageType = packageType == null ? "source" : packageType;
    }

    public boolean processor() {
        return processor || "processor".equals(packageType);
    }

    public void setProcessor(boolean processor) {
        this.processor = processor;
        if (processor && !"processor".equals(packageType)) {
            this.packageType = "processor";
        }
    }

    public String processorEntry() {
        return processorEntry;
    }

    public void setProcessorEntry(String processorEntry) {
        this.processorEntry = processorEntry == null ? "" : processorEntry;
    }

    public int minProcessorApi() {
        return minProcessorApi;
    }

    public void setMinProcessorApi(int minProcessorApi) {
        this.minProcessorApi = minProcessorApi;
    }

    public int maxProcessorApi() {
        return maxProcessorApi;
    }

    public void setMaxProcessorApi(int maxProcessorApi) {
        this.maxProcessorApi = maxProcessorApi;
    }

    public Map<String, String> permissions() {
        return permissions;
    }

    /** All production dependencies for a compile target triple. */
    public List<Dependency> effectiveDependencies(String triple, boolean includeDev) {
        List<Dependency> out = new ArrayList<>(dependencies);
        String scope = scopeForTriple(triple);
        List<Dependency> scoped = targetDependencies.getOrDefault(scope, List.of());
        out.addAll(scoped);
        if (includeDev) {
            out.addAll(devDependencies);
        }
        return out;
    }

    public static String scopeForTriple(String triple) {
        if (triple == null) {
            return "native";
        }
        String t = triple.toLowerCase();
        if (t.contains("wasm") || t.contains("browser")) {
            return "browser";
        }
        if (t.contains("desktop")) {
            return "desktop";
        }
        return "native";
    }

    public record NativeLibrarySpec(
            String name,
            String platform,
            String link,
            String runtimePath
    ) {
    }
}
