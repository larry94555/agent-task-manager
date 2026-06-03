package com.example.simpleagent.demo;

public class ActionExecutionResult {
    private final boolean success;
    private final String observation;

    private ActionExecutionResult(boolean success, String observation) {
        this.success = success;
        this.observation = observation;
    }

    public static ActionExecutionResult success(String observation) {
        return new ActionExecutionResult(true, observation);
    }

    public static ActionExecutionResult failure(String observation) {
        return new ActionExecutionResult(false, observation);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getObservation() {
        return observation;
    }
}
