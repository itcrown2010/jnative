/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.target;

import io.jnative.backend.TargetCapability;

/**
 * Maps a recognizable user-source pattern to a required package capability.
 *
 * @author 张晓达
 */
public record ApiCapabilityRule(
        String pattern,
        TargetCapability required,
        String featureLabel
) {
}
