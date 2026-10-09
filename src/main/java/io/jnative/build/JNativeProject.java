package io.jnative.build;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Multi-module JNative project (0.29).
 * @author 张晓达
*/
public final class JNativeProject {
    private final String name;
    private final String root;
    private final Map<String, Module> modules = new LinkedHashMap<>();
    private final List<BuildTargetSpec> targets = new ArrayList<>();
    private String cacheDir = ".jnative/cache";
    private String buildDir = ".jnative/build";
    private String generatedDir = ".jnative/generated";

    public JNativeProject(String name, String root) {
        this.name = name;
        this.root = root;
    }

    public String name() {
        return name;
    }

    public String root() {
        return root;
    }

    public Map<String, Module> modules() {
        return modules;
    }

    public List<BuildTargetSpec> targets() {
        return targets;
    }

    public String cacheDir() {
        return cacheDir;
    }

    public void setCacheDir(String cacheDir) {
        this.cacheDir = cacheDir;
    }

    public String buildDir() {
        return buildDir;
    }

    public void setBuildDir(String buildDir) {
        this.buildDir = buildDir;
    }

    public String generatedDir() {
        return generatedDir;
    }

    public void setGeneratedDir(String generatedDir) {
        this.generatedDir = generatedDir;
    }

    public Module module(String name) {
        return modules.get(name);
    }
}
