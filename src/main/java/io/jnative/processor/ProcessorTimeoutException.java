package io.jnative.processor;

/**
 * @author 张晓达
 */
public final class ProcessorTimeoutException extends RuntimeException {
    public ProcessorTimeoutException(String processorName) {
        super(processorName);
    }
}
