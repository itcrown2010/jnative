package io.jnative.pkg;

import io.jnative.target.TargetId;
import io.jnative.target.TargetProfiles;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Package feature flags ({@code requires = [...]}).
 * Provided sets come from {@link TargetProfiles} — must stay aligned with
 * {@link io.jnative.backend.TargetCapability}.
 *
 * @author 张晓达
 */
public enum TargetCapability {
    FILESYSTEM,
    RAW_SOCKET,
    THREADS,
    NATIVE_FFI,
    TLS_NATIVE,
    HTTP2_NATIVE,
    BROWSER_DOM,
    JS_INTEROP,
    BROWSER_FETCH,
    PROCESS,
    DATABASE,
    DESKTOP_UI,
    /** Host JVM bridge for tooling only — not a runtime mobile/native claim. */
    JAVA_BRIDGE;

    /**
     * Capabilities provided by a compile/package triple.
     * {@code wasm32-browser} and mobile containers do <em>not</em> provide THREADS.
     */
    public static Set<TargetCapability> forTriple(String triple) {
        TargetId id = TargetId.fromCompileTriple(triple);
        EnumSet<TargetCapability> set = EnumSet.noneOf(TargetCapability.class);
        for (io.jnative.backend.TargetCapability c : TargetProfiles.packageFlags(id)) {
            try {
                set.add(TargetCapability.valueOf(c.name()));
            } catch (IllegalArgumentException ignored) {
                // ignore backend-only names
            }
        }
        return set;
    }

    public static TargetCapability parse(String name) {
        return TargetCapability.valueOf(name.trim().toUpperCase(Locale.ROOT));
    }

    public static Set<TargetCapability> parseAll(Iterable<String> names) {
        EnumSet<TargetCapability> set = EnumSet.noneOf(TargetCapability.class);
        if (names == null) {
            return set;
        }
        for (String n : names) {
            if (n != null && !n.isBlank()) {
                set.add(parse(n));
            }
        }
        return set;
    }

    public static String describeMissing(Set<TargetCapability> required, Set<TargetCapability> provided) {
        StringBuilder sb = new StringBuilder();
        Set<TargetCapability> prov = provided == null ? Collections.emptySet() : provided;
        if (required == null) {
            return "";
        }
        for (TargetCapability c : required) {
            if (!prov.contains(c)) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(c.name());
            }
        }
        return sb.toString();
    }
}
