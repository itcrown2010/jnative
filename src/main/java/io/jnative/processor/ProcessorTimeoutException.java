package io.jnative.processor;

public final class ProcessorTimeoutException extends RuntimeException {
    public ProcessorTimeoutException(String processorName) {
        super(processorName);
    }
}
