/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.target;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Capability profile for one {@link TargetId}.
 *
 * @author 张晓达
 */
public final class TargetProfile {
    private final TargetId id;
    private final String description;
    private final EnumMap<Capability, CapabilityEvidence> evidence;
    private final Set<io.jnative.backend.TargetCapability> packageFlags;

    public TargetProfile(
            TargetId id,
            String description,
            Map<Capability, CapabilityEvidence> evidence,
            Set<io.jnative.backend.TargetCapability> packageFlags
    ) {
        this.id = Objects.requireNonNull(id);
        this.description = description == null ? "" : description;
        this.evidence = new EnumMap<>(Capability.class);
        if (evidence != null) {
            this.evidence.putAll(evidence);
        }
        this.packageFlags = packageFlags == null
                ? EnumSet.noneOf(io.jnative.backend.TargetCapability.class)
                : EnumSet.copyOf(packageFlags);
    }

    public TargetId id() {
        return id;
    }

    public String description() {
        return description;
    }

    public CapabilityEvidence evidenceOf(Capability c) {
        return evidence.getOrDefault(c, CapabilityEvidence.UNVERIFIED);
    }

    public Map<Capability, CapabilityEvidence> evidence() {
        return Collections.unmodifiableMap(evidence);
    }

    /** Package feature flags this target provides (for {@code requires = [...]} checks). */
    public Set<io.jnative.backend.TargetCapability> packageFlags() {
        return Collections.unmodifiableSet(packageFlags);
    }

    public boolean providesPackageFlag(io.jnative.backend.TargetCapability flag) {
        return packageFlags.contains(flag);
    }

    public boolean supports(Capability c) {
        CapabilityEvidence e = evidenceOf(c);
        return e == CapabilityEvidence.IMPLEMENTED || e == CapabilityEvidence.VERIFIED;
    }
}
