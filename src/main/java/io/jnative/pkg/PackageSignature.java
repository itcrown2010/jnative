/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.pkg;

/** Foundation for publisher signatures (verification hooks for future Registry transparency).
 * @author 张晓达
 */
public final class PackageSignature {
    private final String algorithm;
    private final String publicKeyId;
    private final String signatureHex;

    public PackageSignature(String algorithm, String publicKeyId, String signatureHex) {
        this.algorithm = algorithm == null ? "ed25519" : algorithm;
        this.publicKeyId = publicKeyId == null ? "" : publicKeyId;
        this.signatureHex = signatureHex == null ? "" : signatureHex;
    }

    public String algorithm() {
        return algorithm;
    }

    public String publicKeyId() {
        return publicKeyId;
    }

    public String signatureHex() {
        return signatureHex;
    }

    public boolean present() {
        return !signatureHex.isEmpty();
    }

    /** Download success ≠ trusted. Signature must be verified when present. */
    public boolean verifyPlaceholder(byte[] content, String expectedHash) {
        if (!present()) {
            return false; // unsigned ≠ verified
        }
        // Foundation: real crypto verification lands with registry CA
        return expectedHash != null && !expectedHash.isEmpty() && content != null;
    }
}
