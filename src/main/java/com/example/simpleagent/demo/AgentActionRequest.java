package com.example.simpleagent.demo;

import java.util.LinkedHashMap;
import java.util.Map;

public class AgentActionRequest {
    private String type;
    private String content;
    private String action;
    private Map<String, Object> input = new LinkedHashMap<>();

    public AgentActionRequest() {
    }

    public boolean isFinal() {
        return "final".equalsIgnoreCase(type);
    }

    public boolean isAction() {
        return "action".equalsIgnoreCase(type);
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }


    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }


    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }


    public Map<String, Object> getInput() {
        if (input == null) {
            input = new LinkedHashMap<>();
        }
        return input;
    }

    public void setInput(Map<String, Object> input) {
        this.input = input == null ? new LinkedHashMap<>() : input;
    }
}
