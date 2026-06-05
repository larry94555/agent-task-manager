package com.example.simpleagent.demo;

public class ClientTaskAction {
    private String type;
    private Integer taskId;
    private Integer parentTaskId;
    private String name;
    private Boolean switchToTask;

    public ClientTaskAction() {
    }

    public ClientTaskAction(String type, Integer taskId, Integer parentTaskId, String name, Boolean switchToTask) {
        this.type = type;
        this.taskId = taskId;
        this.parentTaskId = parentTaskId;
        this.name = name;
        this.switchToTask = switchToTask;
    }

    public static ClientTaskAction createTask(String name, Integer parentTaskId, boolean switchToTask) {
        return new ClientTaskAction("create_task", null, parentTaskId, name, switchToTask);
    }

    public static ClientTaskAction renameTask(Integer taskId, String name) {
        return new ClientTaskAction("rename_task", taskId, null, name, false);
    }

    public static ClientTaskAction closeTask(Integer taskId) {
        return new ClientTaskAction("close_task", taskId, null, null, false);
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getTaskId() {
        return taskId;
    }

    public void setTaskId(Integer taskId) {
        this.taskId = taskId;
    }

    public Integer getParentTaskId() {
        return parentTaskId;
    }

    public void setParentTaskId(Integer parentTaskId) {
        this.parentTaskId = parentTaskId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getSwitchToTask() {
        return switchToTask;
    }

    public void setSwitchToTask(Boolean switchToTask) {
        this.switchToTask = switchToTask;
    }
}

