/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.processor;

/**
 * @author 张晓达
 */
public final class ProcessorCrashException extends RuntimeException {
    private final String processorName;

    public ProcessorCrashException(String processorName, Throwable cause) {
        super(cause == null ? "unknown" : cause.getMessage(), cause);
        this.processorName = processorName;
    }

    public String processorName() {
        return processorName;
    }
}
