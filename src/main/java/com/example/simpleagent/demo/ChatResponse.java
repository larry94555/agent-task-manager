package com.example.simpleagent.demo;

import java.util.ArrayList;
import java.util.List;

public class ChatResponse {
    private String content;
    private List<ClientTaskAction> taskActions = new ArrayList<>();

    public ChatResponse() {
    }

    public ChatResponse(String content) {
        this.content = content;
    }

    public ChatResponse(String content, List<ClientTaskAction> taskActions) {
        this.content = content;
        this.taskActions = taskActions == null ? new ArrayList<>() : taskActions;
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
}

