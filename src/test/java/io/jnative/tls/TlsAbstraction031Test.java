package io.jnative.tls;

import io.jnative.Main;
import io.jnative.backend.CompileTarget;
import io.jnative.backend.TargetCapability;
import io.jnative.build.BuildReport031;
import io.jnative.stdlib.StdlibIncluder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Host-side tests for JNative 0.31 TLS / HTTP/2 abstraction (no OpenSSL). */
class TlsAbstraction031Test {

    @Test
    void mainVersionIs031() {
        assertEquals("0.31", Main.VERSION);
    }

    @Test
    void backendTargetCapabilitiesExist() {
        assertNotNull(TargetCapability.valueOf("TLS_NATIVE"));
        assertNotNull(TargetCapability.valueOf("HTTP2_NATIVE"));
        assertNotNull(TargetCapability.valueOf("BROWSER_FETCH"));
    }

    @Test
    void pkgTargetCapabilitiesExist() {
        assertNotNull(io.jnative.pkg.TargetCapability.valueOf("TLS_NATIVE"));
        assertNotNull(io.jnative.pkg.TargetCapability.valueOf("HTTP2_NATIVE"));
        assertNotNull(io.jnative.pkg.TargetCapability.valueOf("BROWSER_FETCH"));
    }

    @Test
    void nativeHostIncludesTlsAndHttp2NotBrowserFetch() {
        Set<TargetCapability> caps = CompileTarget.NATIVE_HOST.capabilities();
        assertTrue(caps.contains(TargetCapability.TLS_NATIVE));
        assertTrue(caps.contains(TargetCapability.HTTP2_NATIVE));
        assertFalse(caps.contains(TargetCapability.BROWSER_FETCH));
    }

    @Test
    void wasmBrowserIncludesBrowserFetchNotTlsNative() {
        Set<TargetCapability> caps = CompileTarget.WASM32_BROWSER.capabilities();
        assertTrue(caps.contains(TargetCapability.BROWSER_FETCH));
        assertFalse(caps.contains(TargetCapability.TLS_NATIVE));
    }

    @Test
    void pkgNativeTripleIncludesTlsAndHttp2() {
        Set<io.jnative.pkg.TargetCapability> caps =
                io.jnative.pkg.TargetCapability.forTriple("native");
        assertTrue(caps.contains(io.jnative.pkg.TargetCapability.TLS_NATIVE));
        assertTrue(caps.contains(io.jnative.pkg.TargetCapability.HTTP2_NATIVE));
        assertFalse(caps.contains(io.jnative.pkg.TargetCapability.BROWSER_FETCH));
    }

    @Test
    void pkgWasmTripleIncludesBrowserFetchNotTlsNative() {
        Set<io.jnative.pkg.TargetCapability> caps =
                io.jnative.pkg.TargetCapability.forTriple("wasm32-browser");
        assertTrue(caps.contains(io.jnative.pkg.TargetCapability.BROWSER_FETCH));
        assertFalse(caps.contains(io.jnative.pkg.TargetCapability.TLS_NATIVE));
    }

    @Test
    void tlsConfigBuilderDefaultsFromStdlib() throws IOException {
        String tlsLib = loadResource("stdlib/tls/TlsLib.java");
        assertTrue(tlsLib.contains("insecureSkipVerify = 0"), "verify on by default");
        assertTrue(tlsLib.contains("alpn = \"h2,http/1.1\""), "ALPN default h2,http/1.1");
    }

    @Test
    void stdlibIncluderNeedsTlsDetectsTlsConnectionAndListenTls() {
        assertTrue(StdlibIncluder.needsTls("TlsConnection c = TlsConnection.connect(h, p, cfg);"));
        assertTrue(StdlibIncluder.needsTls("void main() { listenTls(443); }"));
        assertFalse(StdlibIncluder.needsTls("HttpClient.get(url);"));
    }

    @Test
    void stdlibIncluderNeedsHttp2DetectsHttp2Types() {
        assertTrue(StdlibIncluder.needsHttp2("Http2Client.request(tls, req, url);"));
        assertTrue(StdlibIncluder.needsHttp2("Http2Connection conn = new Http2Connection(tls);"));
        assertFalse(StdlibIncluder.needsHttp2("HttpClient.get(url);"));
    }

    @Test
    void stdlibPrependOrderTlsBeforeHttpHttp2AfterHttp() throws IOException {
        String merged = StdlibIncluder.prependStdlibs(
                "class App { void run() { Http2Client.request(null, null, null); TlsConnection.connect(\"h\", 443, null); } }");
        int tlsIdx = merged.indexOf("// JNative TLS stdlib (0.31)");
        int httpIdx = merged.indexOf("class HttpClient");
        int http2Idx = merged.indexOf("// JNative HTTP/2 (0.31)");
        assertTrue(tlsIdx >= 0);
        assertTrue(httpIdx >= 0);
        assertTrue(http2Idx >= 0);
        assertTrue(tlsIdx < httpIdx, "TlsLib before HttpLib");
        assertTrue(httpIdx < http2Idx, "Http2Lib after HttpLib");
    }

    @Test
    void buildReportStringConstantsExist() {
        assertEquals("TLS compiler intrinsics: NONE", BuildReport031.TLS_COMPILER_INTRINSICS);
        assertEquals("HTTP2 compiler intrinsics: NONE", BuildReport031.HTTP2_COMPILER_INTRINSICS);
        assertEquals("Browser fetch compiler intrinsics: NONE", BuildReport031.BROWSER_FETCH_COMPILER_INTRINSICS);
    }

    @Test
    void reportStringsReferencedInStdlibComments() throws IOException {
        String tls = loadResource("stdlib/tls/TlsLib.java");
        String http2 = loadResource("stdlib/http/Http2Lib.java");
        assertTrue(tls.contains("NOT a compiler intrinsic"));
        assertTrue(http2.contains("NOT a compiler intrinsic"));
    }

    private static String loadResource(String path) throws IOException {
        try (InputStream in = TlsAbstraction031Test.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "missing resource: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
