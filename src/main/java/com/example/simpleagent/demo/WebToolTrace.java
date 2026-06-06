package com.example.simpleagent.demo;

import java.time.Duration;
import java.time.Instant;

public class WebToolTrace {
    private int step;
    private String action;
    private String input;
    private String startedAt;
    private String completedAt;
    private long durationMs;
    private boolean success;
    private String errorCode;
    private String observation;
    private String observationPreview;

    public WebToolTrace() {
    }

    public static WebToolTrace completed(
            int step,
            String action,
            String input,
            Instant startedAt,
            long startedNanos,
            ActionExecutionResult result
    ) {
        WebToolTrace trace = new WebToolTrace();
        trace.step = step;
        trace.action = action == null ? "" : action;
        trace.input = input == null ? "{}" : input;
        trace.startedAt = startedAt == null ? null : startedAt.toString();
        trace.completedAt = Instant.now().toString();
        trace.durationMs = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
        trace.success = result != null && result.isSuccess();
        trace.errorCode = result == null ? WebToolErrorCode.INTERNAL_ERROR.name() : result.getErrorCode();
        trace.observation = result == null ? "Action did not return a result." : nullToEmpty(result.getObservation());
        trace.observationPreview = preview(trace.observation, 500);
        return trace;
    }

    private static String preview(String value, int maxChars) {
        String normalized = nullToEmpty(value).replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxChars)) + "...";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public int getStep() {
        return step;
    }

    public void setStep(int step) {
        this.step = step;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
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

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getObservation() {
        return observation;
    }

    public void setObservation(String observation) {
        this.observation = observation;
    }

    public String getObservationPreview() {
        return observationPreview;
    }

    public void setObservationPreview(String observationPreview) {
        this.observationPreview = observationPreview;
    }
}