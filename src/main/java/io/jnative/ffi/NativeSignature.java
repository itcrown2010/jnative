/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.ffi;

import java.util.List;

/**
 * @author 张晓达
 */
public record NativeSignature(AbiType returnType, List<AbiType> parameters) {
    public NativeSignature {
        parameters = List.copyOf(parameters);
    }
}
