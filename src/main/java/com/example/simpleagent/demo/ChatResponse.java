package com.example.simpleagent.demo;

public class ChatResponse {
    private String content;
    private String updatedSummary;

    public ChatResponse() {
    }

    public ChatResponse(String content, String updatedSummary) {
        this.content = content;
        this.updatedSummary = updatedSummary;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getUpdatedSummary() {
        return updatedSummary;
    }

    public void setUpdatedSummary(String updatedSummary) {
        this.updatedSummary = updatedSummary;
    }
}