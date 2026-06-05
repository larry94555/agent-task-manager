package com.example.simpleagent.demo;

public class LlamaCompletionException extends RuntimeException {
    private final ModelCallTrace trace;

    public LlamaCompletionException(String message, ModelCallTrace trace, Throwable cause) {
        super(message, cause);
        this.trace = trace;
    }

    public ModelCallTrace getTrace() {
        return trace;
    }
}
