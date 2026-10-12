/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.incremental;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Content-addressed hashing (SHA-256). Prefer over mtime for correctness.
 * @author 张晓达
 */
public final class ContentHasher {
    private ContentHasher() {
    }

    public static String sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(data == null ? new byte[0] : data);
            return toHex(dig);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static String sha256(String text) {
        return sha256(text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8));
    }

    public static String shortHash(String text) {
        String h = sha256(text);
        return h.substring(0, 16);
    }

    private static String toHex(byte[] dig) {
        StringBuilder sb = new StringBuilder(dig.length * 2);
        for (byte b : dig) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
}
