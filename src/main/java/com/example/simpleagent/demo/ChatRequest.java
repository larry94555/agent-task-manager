package com.example.simpleagent.demo;

import java.util.List;

public class ChatRequest {
    private Integer taskId;
    private List<String> context;
    private String latest;
    private List<TaskSnapshot> tasks;

    public Integer getTaskId() {
        return taskId;
    }

    public void setTaskId(Integer taskId) {
        this.taskId = taskId;
    }

    public List<String> getContext() {
        return context;
    }

    public void setContext(List<String> context) {
        this.context = context;
    }

    public String getLatest() {
        return latest;
    }

    public void setLatest(String latest) {
        this.latest = latest;
    }

    public List<TaskSnapshot> getTasks() {
        return tasks;
    }

    public void setTasks(List<TaskSnapshot> tasks) {
        this.tasks = tasks;
    }
}