package io.jnative.build;

import java.util.ArrayList;
import java.util.List;

/** One logical module in a JNative project (shared / server / browser / desktop).
 * @author 张晓达
*/
public final class Module {
    private final String name;
    private final List<String> sourceRoots = new ArrayList<>();
    private final List<String> resourceRoots = new ArrayList<>();
    private final List<String> dependsOn = new ArrayList<>();
    private String kind = "lib"; // lib | server | browser | desktop

    public Module(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public List<String> sourceRoots() {
        return sourceRoots;
    }

    public List<String> resourceRoots() {
        return resourceRoots;
    }

    public List<String> dependsOn() {
        return dependsOn;
    }

    public String kind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }
}
