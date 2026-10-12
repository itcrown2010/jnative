/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.processor;

/**
 * @author 张晓达
 */
public final class ProcessorTimeoutException extends RuntimeException {
    public ProcessorTimeoutException(String processorName) {
        super(processorName);
    }
}
