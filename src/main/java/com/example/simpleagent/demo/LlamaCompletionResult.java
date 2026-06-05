package com.example.simpleagent.demo;

public class LlamaCompletionResult {
    private final String content;
    private final ModelCallTrace trace;

    public LlamaCompletionResult(String content, ModelCallTrace trace) {
        this.content = content;
        this.trace = trace;
    }

    public String getContent() {
        return content;
    }

    public ModelCallTrace getTrace() {
        return trace;
    }
}
