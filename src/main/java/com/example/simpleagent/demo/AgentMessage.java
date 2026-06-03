package com.example.simpleagent.demo;

public class AgentMessage {
    private String role;
    private String content;

    public AgentMessage() {
    }

    public AgentMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public static AgentMessage system(String content) {
        return new AgentMessage("system", content);
    }

    public static AgentMessage user(String content) {
        return new AgentMessage("user", content);
    }

    public static AgentMessage assistant(String content) {
        return new AgentMessage("assistant", content);
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }


    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
