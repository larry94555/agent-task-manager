package com.example.simpleagent.demo;

import java.time.Duration;
import java.time.Instant;

public class WebToolTrace {
    private int step;
    private String displayStep;
    private String traceType;
    private String planId;
    private String planTitle;
    private String planStepId;
    private String planStepTitle;
    private String planStepGoal;
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
        trace.displayStep = "Step " + step;
        trace.traceType = "tool_action";
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

    public static WebToolTrace plan(
            int step,
            String planId,
            String planTitle,
            String input,
            String observation
    ) {
        WebToolTrace trace = new WebToolTrace();
        trace.step = step;
        trace.displayStep = planId == null || planId.isBlank() ? "Plan" : planId;
        trace.traceType = "plan";
        trace.planId = planId;
        trace.planTitle = planTitle;
        trace.action = "execution_plan";
        trace.input = input == null ? "{}" : input;
        trace.startedAt = Instant.now().toString();
        trace.completedAt = trace.startedAt;
        trace.durationMs = 0;
        trace.success = true;
        trace.errorCode = null;
        trace.observation = nullToEmpty(observation);
        trace.observationPreview = preview(trace.observation, 500);
        return trace;
    }

    public WebToolTrace withPlanContext(
            String planId,
            String planTitle,
            String planStepId,
            String planStepTitle,
            String planStepGoal
    ) {
        this.traceType = "plan_step";
        this.planId = planId;
        this.planTitle = planTitle;
        this.planStepId = planStepId;
        this.planStepTitle = planStepTitle;
        this.planStepGoal = planStepGoal;
        this.displayStep = planStepId == null || planStepId.isBlank() ? this.displayStep : "Step " + planStepId;
        return this;
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

    public int getStep() { return step; }
    public void setStep(int step) { this.step = step; }
    public String getDisplayStep() { return displayStep; }
    public void setDisplayStep(String displayStep) { this.displayStep = displayStep; }
    public String getTraceType() { return traceType; }
    public void setTraceType(String traceType) { this.traceType = traceType; }
    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }
    public String getPlanTitle() { return planTitle; }
    public void setPlanTitle(String planTitle) { this.planTitle = planTitle; }
    public String getPlanStepId() { return planStepId; }
    public void setPlanStepId(String planStepId) { this.planStepId = planStepId; }
    public String getPlanStepTitle() { return planStepTitle; }
    public void setPlanStepTitle(String planStepTitle) { this.planStepTitle = planStepTitle; }
    public String getPlanStepGoal() { return planStepGoal; }
    public void setPlanStepGoal(String planStepGoal) { this.planStepGoal = planStepGoal; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getInput() { return input; }
    public void setInput(String input) { this.input = input; }
    public String getStartedAt() { return startedAt; }
    public void setStartedAt(String startedAt) { this.startedAt = startedAt; }
    public String getCompletedAt() { return completedAt; }
    public void setCompletedAt(String completedAt) { this.completedAt = completedAt; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getObservation() { return observation; }
    public void setObservation(String observation) { this.observation = observation; }
    public String getObservationPreview() { return observationPreview; }
    public void setObservationPreview(String observationPreview) { this.observationPreview = observationPreview; }
}