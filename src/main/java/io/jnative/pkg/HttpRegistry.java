package io.jnative.pkg;

import io.jnative.incremental.ContentHasher;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP registry client (HTTPS required except localhost).
 * Protocol: GET /search?q=  /pkg/{ns}/{name}/versions  /pkg/{ns}/{name}/{ver}/meta  /pkg/.../download
 */
public final class HttpRegistry implements PackageRegistry {
    private final String baseUrl;
    private final String name;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public HttpRegistry(String baseUrl) {
        this(baseUrl, "remote", 5_000, 30_000);
    }

    public HttpRegistry(String baseUrl, String name, int connectTimeoutMs, int readTimeoutMs) {
        String u = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        validateUrl(u);
        this.baseUrl = u;
        this.name = name;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    private static void validateUrl(String u) {
        String lower = u.toLowerCase();
        if (lower.startsWith("https://")) {
            return;
        }
        if (lower.startsWith("http://127.0.0.1") || lower.startsWith("http://localhost")
                || lower.startsWith("http://[::1]")) {
            return;
        }
        throw new IllegalArgumentException("remote registry must use HTTPS (got " + u + ")");
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String url() {
        return baseUrl;
    }

    @Override
    public List<PackageId> search(String query) throws IOException {
        String body = get(baseUrl + "/search?q=" + encode(query == null ? "" : query));
        List<PackageId> out = new ArrayList<>();
        Matcher m = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
        while (m.find()) {
            out.add(PackageId.parse(m.group(1)).withSource(baseUrl));
        }
        return out;
    }

    @Override
    public List<SemanticVersion> versions(PackageId id) throws IOException {
        String body = get(baseUrl + "/pkg/" + id.namespace() + "/" + id.name() + "/versions");
        List<SemanticVersion> out = new ArrayList<>();
        Matcher m = Pattern.compile("\"([0-9][^\"]*)\"").matcher(body);
        while (m.find()) {
            try {
                out.add(SemanticVersion.parse(m.group(1)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return out;
    }

    @Override
    public Optional<DependencyResolver.PackageMeta> metadata(PackageId id, SemanticVersion version) throws IOException {
        String body = get(baseUrl + "/pkg/" + id.namespace() + "/" + id.name() + "/" + version + "/meta");
        String hash = extract(body, "hash");
        if (hash.startsWith("sha256:")) {
            hash = hash.substring(7);
        }
        boolean yanked = body.contains("\"yanked\":true") || body.contains("\"yanked\": true");
        return Optional.of(new DependencyResolver.PackageMeta(
                id.withSource(baseUrl),
                version,
                List.of(),
                java.util.Set.of(),
                java.util.Set.of(),
                hash,
                baseUrl,
                yanked,
                "source",
                "",
                List.of()
        ));
    }

    @Override
    public byte[] download(PackageId id, SemanticVersion version) throws IOException {
        String url = baseUrl + "/pkg/" + id.namespace() + "/" + id.name() + "/" + version + "/download";
        return getBytesSecure(url);
    }

    @Override
    public void publish(PackageId id, SemanticVersion version, byte[] archive, DependencyResolver.PackageMeta meta)
            throws IOException {
        throw new IOException("HTTP publish not enabled in this client; use FilesystemRegistry or authenticated endpoint");
    }

    @Override
    public void yank(PackageId id, SemanticVersion version) throws IOException {
        throw new IOException("HTTP yank requires authenticated registry API");
    }

    @Override
    public boolean isYanked(PackageId id, SemanticVersion version) throws IOException {
        return metadata(id, version).map(DependencyResolver.PackageMeta::yanked).orElse(false);
    }

    private String get(String url) throws IOException {
        return new String(getBytesSecure(url), StandardCharsets.UTF_8);
    }

    private byte[] getBytesSecure(String urlStr) throws IOException {
        URI uri = URI.create(urlStr);
        URL url = uri.toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(connectTimeoutMs);
        conn.setReadTimeout(readTimeoutMs);
        conn.setInstanceFollowRedirects(false);
        int code = conn.getResponseCode();
        if (code >= 300 && code < 400) {
            String loc = conn.getHeaderField("Location");
            if (loc == null) {
                throw new IOException("redirect without Location");
            }
            // only allow same-host HTTPS redirects
            URI next = uri.resolve(loc);
            validateUrl(next.getScheme() + "://" + next.getHost()
                    + (next.getPort() > 0 ? ":" + next.getPort() : ""));
            if (!next.getHost().equalsIgnoreCase(uri.getHost())) {
                throw new IOException("SECURITY ERROR: cross-host redirect refused");
            }
            return getBytesSecure(next.toString());
        }
        if (code == 404) {
            throw new IOException("404 " + urlStr);
        }
        if (code != 200) {
            throw new IOException("HTTP " + code + " for " + urlStr);
        }
        try (InputStream in = conn.getInputStream()) {
            return in.readAllBytes();
        }
    }

    private static String extract(String body, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
        return m.find() ? m.group(1) : "";
    }

    private static String encode(String q) {
        return java.net.URLEncoder.encode(q, StandardCharsets.UTF_8);
    }
}
