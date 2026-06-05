package com.example.simpleagent.demo;

public class TaskSnapshot {
    private Integer id;
    private String name;
    private String status;
    private String lifecycle;
    private Integer parentTaskId;
    private String createdBy;

    public TaskSnapshot() {
    }

    public TaskSnapshot(Integer id, String name, String status, String lifecycle, Integer parentTaskId, String createdBy) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.lifecycle = lifecycle;
        this.parentTaskId = parentTaskId;
        this.createdBy = createdBy;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLifecycle() {
        return lifecycle;
    }

    public void setLifecycle(String lifecycle) {
        this.lifecycle = lifecycle;
    }

    public Integer getParentTaskId() {
        return parentTaskId;
    }

    public void setParentTaskId(Integer parentTaskId) {
        this.parentTaskId = parentTaskId;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
}

