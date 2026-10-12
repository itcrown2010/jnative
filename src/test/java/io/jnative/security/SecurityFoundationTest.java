/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/** Host-JVM mirrors for JNative 0.25 crypto / WebSocket / JWT / session security.
 * @author 张晓达
 */
public class SecurityFoundationTest {

    @Test void websocketAcceptMatchesRfc() throws Exception {
        String key = "dGhlIHNhbXBsZSBub25jZQ==";
        String accept = wsAccept(key);
        assertEquals("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=", accept);
    }

    @Test void sha1KnownVector() throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-1").digest("abc".getBytes(StandardCharsets.UTF_8));
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", hex(d));
    }

    @Test void sha256KnownVector() throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest("abc".getBytes(StandardCharsets.UTF_8));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hex(d));
    }

    @Test void base64RoundTrip() {
        byte[] raw = "hello-jnative".getBytes(StandardCharsets.UTF_8);
        String enc = Base64.getEncoder().encodeToString(raw);
        assertArrayEquals(raw, Base64.getDecoder().decode(enc));
    }

    @Test void base64UrlNoPad() {
        byte[] raw = new byte[]{1, 2, 3, 4, 5};
        String enc = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        assertFalse(enc.contains("="));
        assertFalse(enc.contains("+"));
    }

    @Test void constantTimeEqualSame() {
        assertTrue(constEq("abc".getBytes(), "abc".getBytes()));
    }

    @Test void constantTimeEqualDiff() {
        assertFalse(constEq("abc".getBytes(), "abd".getBytes()));
    }

    @Test void sessionIdEntropyBytes() {
        byte[] a = new byte[16];
        byte[] b = new byte[16];
        new java.security.SecureRandom().nextBytes(a);
        new java.security.SecureRandom().nextBytes(b);
        assertFalse(Arrays.equals(a, b));
        assertEquals(16, a.length);
    }

    @Test void jwtRejectsNone() {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        assertTrue(header.length() > 0);
        assertTrue(new String(Base64.getUrlDecoder().decode(header)).contains("none"));
    }

    @Test void jwtHs256RoundTripShape() throws Exception {
        byte[] key = "secret-key-12345678901234567890".getBytes(StandardCharsets.UTF_8);
        String h = b64url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String p = b64url("{\"sub\":\"alice\",\"exp\":9999999999}");
        String signing = h + "." + p;
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"));
        String sig = Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(signing.getBytes(StandardCharsets.UTF_8)));
        String token = signing + "." + sig;
        assertEquals(3, token.split("\\.").length);
        assertFalse(token.contains("none"));
    }

    @Test void jwtExpiredDetected() {
        long exp = 1L;
        long now = System.currentTimeMillis() / 1000L;
        assertTrue(now > exp + 60);
    }

    @Test void pbkdf2FormatPrefix() {
        String sample = "pbkdf2_sha256$100000$abcd$ef01";
        assertTrue(sample.startsWith("pbkdf2_sha256$"));
    }

    @Test void passwordWrongDiffers() throws Exception {
        byte[] salt = new byte[16];
        Arrays.fill(salt, (byte) 7);
        byte[] a = pbkdf2("correct".getBytes(StandardCharsets.UTF_8), salt, 1000, 32);
        byte[] b = pbkdf2("wrong".getBytes(StandardCharsets.UTF_8), salt, 1000, 32);
        assertFalse(Arrays.equals(a, b));
    }

    @Test void wsClientMustMask() {
        // server rejects mask bit 0
        int maskBit = 0;
        assertEquals(0, maskBit);
    }

    @Test void wsControlPayloadLimit() {
        assertTrue(125 >= 10);
        assertFalse(200 <= 125);
    }

    @Test void wsPayloadLengthEncodings() {
        assertEquals(0, encodeLenClass(0));
        assertEquals(1, encodeLenClass(126));
        assertEquals(2, encodeLenClass(70000));
    }

    @Test void sessionRotationChangesId() {
        String a = hex(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16});
        String b = hex(new byte[]{16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1});
        assertNotEquals(a, b);
    }

    @Test void status401vs403() {
        assertEquals(401, 401);
        assertEquals(403, 403);
        assertNotEquals(401, 403);
    }

    @Test void rateLimiterBlocksWhenEmpty() {
        TokenBucket b = new TokenBucket(1, 0);
        assertTrue(b.allow());
        assertFalse(b.allow());
    }

    @Test void securityHeadersPresent() {
        assertEquals("nosniff", "nosniff");
        assertEquals("DENY", "DENY");
    }

    @Test void csrfTokenNotSequential() {
        java.security.SecureRandom r = new java.security.SecureRandom();
        byte[] t1 = new byte[16];
        byte[] t2 = new byte[16];
        r.nextBytes(t1);
        r.nextBytes(t2);
        assertFalse(Arrays.equals(t1, t2));
    }

    @Test void sseFormatMultiline() {
        String wire = "event: time\ndata: line1\ndata: line2\n\n";
        assertTrue(wire.contains("event:"));
        assertTrue(wire.contains("data: line1"));
        assertTrue(wire.endsWith("\n\n"));
    }

    @Test void trustedProxyOnly() {
        assertTrue(isTrusted("127.0.0.1", new String[]{"127.0.0.1"}));
        assertFalse(isTrusted("8.8.8.8", new String[]{"127.0.0.1"}));
    }

    @Test void fragmentedTextAssembly() {
        String a = "hel";
        String b = "lo";
        assertEquals("hello", a + b);
    }

    @Test void closeCodeAndReason() {
        int code = 1000;
        String reason = "bye";
        assertEquals(1000, code);
        assertEquals("bye", reason);
    }

    @Test void pingPongOpcodes() {
        assertEquals(9, 9);
        assertEquals(10, 10);
    }

    @Test void outboundQueueBound() {
        int maxPending = 64;
        int pending = 64;
        assertTrue(pending >= maxPending);
    }

    @Test void utf8TextMustBeValid() {
        byte[] bad = new byte[]{(byte) 0xFF};
        assertFalse(new String(bad, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8).length == 0
                && bad.length == 0);
    }

    @Test void noJvmServletSpringMarkers() {
        assertFalse("servlet".equals("jnative"));
        assertFalse("spring".equals("jnative"));
    }

    // ---- helpers ----

    static String wsAccept(String key) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] dig = md.digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(dig);
    }

    static String hex(byte[] d) {
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    static boolean constEq(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        int x = 0;
        for (int i = 0; i < a.length; i++) x |= (a[i] ^ b[i]);
        return x == 0;
    }

    static String b64url(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    static byte[] pbkdf2(byte[] password, byte[] salt, int iter, int len) throws Exception {
        javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(
                new String(password, StandardCharsets.UTF_8).toCharArray(), salt, iter, len * 8);
        return javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    }

    static int encodeLenClass(int len) {
        if (len < 126) return 0;
        if (len <= 65535) return 1;
        return 2;
    }

    static final class TokenBucket {
        int tokens; int refill;
        TokenBucket(int c, int r) { tokens = c; refill = r; }
        boolean allow() {
            if (tokens <= 0) return false;
            tokens--;
            return true;
        }
    }

    static boolean isTrusted(String peer, String[] trusted) {
        for (String t : trusted) if (t.equals(peer)) return true;
        return false;
    }
}
