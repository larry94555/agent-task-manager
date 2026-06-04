package com.example.simpleagent.demo;

public class ActionExecutionResult {
    private final boolean success;
    private final String observation;
    private final ClientTaskAction clientTaskAction;

    private ActionExecutionResult(boolean success, String observation, ClientTaskAction clientTaskAction) {
        this.success = success;
        this.observation = observation;
        this.clientTaskAction = clientTaskAction;
    }

    public static ActionExecutionResult success(String observation) {
        return new ActionExecutionResult(true, observation, null);
    }

    public static ActionExecutionResult success(String observation, ClientTaskAction clientTaskAction) {
        return new ActionExecutionResult(true, observation, clientTaskAction);
    }

    public static ActionExecutionResult failure(String observation) {
        return new ActionExecutionResult(false, observation, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getObservation() {
        return observation;
    }

    public ClientTaskAction getClientTaskAction() {
        return clientTaskAction;
    }
}

