package com.example.simpleagent.demo;

import java.time.Duration;
import java.time.Instant;

public class ModelCallTrace {
    private int callNumber;
    private String startedAt;
    private String completedAt;
    private long durationMs;
    private int httpStatus;
    private double temperature;
    private int maxTokens;
    private int messageCount;
    private String prompt;
    private String response;
    private String extractedContent;
    private boolean success;
    private String error;

    public ModelCallTrace() {
    }

    public void markStarted(double temperature, int maxTokens, int messageCount, String prompt) {
        this.startedAt = Instant.now().toString();
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.messageCount = messageCount;
        this.prompt = prompt;
        this.success = false;
        this.error = null;
    }

    public void markSuccess(long startedNanos, int httpStatus, String response, String extractedContent) {
        markCompleted(startedNanos);
        this.httpStatus = httpStatus;
        this.response = response;
        this.extractedContent = extractedContent;
        this.success = true;
        this.error = null;
    }

    public void markFailure(long startedNanos, String error) {
        markCompleted(startedNanos);
        this.error = error;
        this.success = false;
    }

    private void markCompleted(long startedNanos) {
        this.completedAt = Instant.now().toString();
        this.durationMs = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
    }

    public int getCallNumber() {
        return callNumber;
    }

    public void setCallNumber(int callNumber) {
        this.callNumber = callNumber;
    }

    public String getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(String startedAt) {
        this.startedAt = startedAt;
    }

    public String getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(String completedAt) {
        this.completedAt = completedAt;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(int messageCount) {
        this.messageCount = messageCount;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public String getExtractedContent() {
        return extractedContent;
    }

    public void setExtractedContent(String extractedContent) {
        this.extractedContent = extractedContent;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
