package com.example.simpleagent.demo;

import java.util.ArrayList;
import java.util.List;

public class ChatRequest {
    private String taskName;
    private String currentMessage;
    private String conversationSummary;
    private List<ChatMessage> recentMessages = new ArrayList<>();

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getCurrentMessage() {
        return currentMessage;
    }

    public void setCurrentMessage(String currentMessage) {
        this.currentMessage = currentMessage;
    }

    public String getConversationSummary() {
        return conversationSummary;
    }

    public void setConversationSummary(String conversationSummary) {
        this.conversationSummary = conversationSummary;
    }

    public List<ChatMessage> getRecentMessages() {
        return recentMessages;
    }

    public void setRecentMessages(List<ChatMessage> recentMessages) {
        this.recentMessages = recentMessages;
    }

    public static class ChatMessage {
        private String role;
        private String content;

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
}