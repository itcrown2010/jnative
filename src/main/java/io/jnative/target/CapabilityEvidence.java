/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.target;

/**
 * Separates "code path exists" from "we have verified it".
 * Declaring support alone must never skip acceptance.
 *
 * @author 张晓达
 */
public enum CapabilityEvidence {
    /** Backend / stdlib implements the feature for this target. */
    IMPLEMENTED,
    /** Implemented and covered by an automated or documented test. */
    VERIFIED,
    /** Explicitly not available on this target. */
    UNSUPPORTED,
    /** Claimed or partially present, but no acceptance evidence yet. */
    UNVERIFIED
}
