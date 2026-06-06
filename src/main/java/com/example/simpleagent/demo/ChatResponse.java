package com.example.simpleagent.demo;

import java.util.ArrayList;
import java.util.List;

public class ChatResponse {
    private String content;
    private List<ClientTaskAction> taskActions = new ArrayList<>();
    private List<ModelCallTrace> modelCallTraces = new ArrayList<>();
    private List<WebToolTrace> webToolTraces = new ArrayList<>();

    public ChatResponse() {
    }

    public ChatResponse(String content) {
        this.content = content;
    }

    public ChatResponse(String content, List<ClientTaskAction> taskActions) {
        this.content = content;
        this.taskActions = taskActions == null ? new ArrayList<>() : taskActions;
    }

    public ChatResponse(
            String content,
            List<ClientTaskAction> taskActions,
            List<ModelCallTrace> modelCallTraces
    ) {
        this.content = content;
        this.taskActions = taskActions == null ? new ArrayList<>() : taskActions;
        this.modelCallTraces = modelCallTraces == null ? new ArrayList<>() : modelCallTraces;
    }

    public ChatResponse(
            String content,
            List<ClientTaskAction> taskActions,
            List<ModelCallTrace> modelCallTraces,
            List<WebToolTrace> webToolTraces
    ) {
        this.content = content;
        this.taskActions = taskActions == null ? new ArrayList<>() : taskActions;
        this.modelCallTraces = modelCallTraces == null ? new ArrayList<>() : modelCallTraces;
        this.webToolTraces = webToolTraces == null ? new ArrayList<>() : webToolTraces;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<ClientTaskAction> getTaskActions() {
        return taskActions;
    }

    public void setTaskActions(List<ClientTaskAction> taskActions) {
        this.taskActions = taskActions == null ? new ArrayList<>() : taskActions;
    }

    public List<ModelCallTrace> getModelCallTraces() {
        return modelCallTraces;
    }

    public void setModelCallTraces(List<ModelCallTrace> modelCallTraces) {
        this.modelCallTraces = modelCallTraces == null ? new ArrayList<>() : modelCallTraces;
    }

    public List<WebToolTrace> getWebToolTraces() {
        return webToolTraces;
    }

    public void setWebToolTraces(List<WebToolTrace> webToolTraces) {
        this.webToolTraces = webToolTraces == null ? new ArrayList<>() : webToolTraces;
    }
}