package com.example.simpleagent.demo;

public class ActionExecutionResult {
    private final boolean success;
    private final String observation;
    private final ClientTaskAction clientTaskAction;
    private final String errorCode;

    private ActionExecutionResult(
            boolean success,
            String observation,
            ClientTaskAction clientTaskAction,
            String errorCode
    ) {
        this.success = success;
        this.observation = observation;
        this.clientTaskAction = clientTaskAction;
        this.errorCode = errorCode;
    }

    public static ActionExecutionResult success(String observation) {
        return new ActionExecutionResult(true, observation, null, null);
    }

    public static ActionExecutionResult success(String observation, ClientTaskAction clientTaskAction) {
        return new ActionExecutionResult(true, observation, clientTaskAction, null);
    }

    public static ActionExecutionResult failure(String observation) {
        return new ActionExecutionResult(false, observation, null, null);
    }

    public static ActionExecutionResult failure(String errorCode, String observation) {
        return new ActionExecutionResult(false, observation, null, errorCode);
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

    public String getErrorCode() {
        return errorCode;
    }
}