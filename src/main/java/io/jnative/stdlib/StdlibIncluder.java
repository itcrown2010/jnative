package io.jnative.stdlib;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Merges bundled JNative stdlib sources into a compilation unit.
 * HTTP is implemented in JNative (not compiler intrinsics).
 */
public final class StdlibIncluder {
    private StdlibIncluder() {
    }

    public static boolean needsHttp(String sourceText) {
        if (sourceText == null) {
            return false;
        }
        return sourceText.contains("HttpClient")
                || sourceText.contains("HttpServer")
                || sourceText.contains("HttpRequest")
                || sourceText.contains("HttpResponse")
                || sourceText.contains("HttpConnection")
                || sourceText.contains("HttpUrl")
                || sourceText.contains("HttpHeaders")
                || sourceText.contains("HttpException");
    }

    public static String prependHttpStdlib(String userSource) throws IOException {
        List<String> parts = new ArrayList<>();
        parts.add(load("stdlib/http/HttpLib.java"));
        parts.add(userSource);
        return String.join("\n\n", parts);
    }

    private static String load(String resource) throws IOException {
        try (InputStream in = StdlibIncluder.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("missing stdlib resource: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
