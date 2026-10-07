package io.jnative.pkg;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/** Target / platform capabilities required or provided. */
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
    JAVA_BRIDGE;

    public static Set<TargetCapability> forTriple(String triple) {
        String t = triple == null ? "native" : triple.toLowerCase(Locale.ROOT);
        EnumSet<TargetCapability> set = EnumSet.noneOf(TargetCapability.class);
        if (t.contains("wasm") || t.contains("browser")) {
            set.add(BROWSER_DOM);
            set.add(JS_INTEROP);
            set.add(BROWSER_FETCH);
            set.add(THREADS);
            return set;
        }
        set.add(FILESYSTEM);
        set.add(RAW_SOCKET);
        set.add(THREADS);
        set.add(NATIVE_FFI);
        set.add(TLS_NATIVE);
        set.add(HTTP2_NATIVE);
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
        for (TargetCapability c : required) {
            if (!provided.contains(c)) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(c.name());
            }
        }
        return sb.toString();
    }
}
