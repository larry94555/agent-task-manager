package com.example.simpleagent.demo;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.util.ArrayList;
import java.util.List;

import java.io.IOException;
import java.util.Map;

@Component
public class LlamaServerManager {

    private Process llamaProcess;
    private final RestTemplate restTemplate = new RestTemplate();

    // Updated to point to llama-server on port 8081
    private final String SERVER_URL = "http://localhost:8081/v1/chat/completions";

    @PostConstruct
    public void startLlamaServer() {
        try {
            // Updated command line arguments to use port 8081
            ProcessBuilder pb = new ProcessBuilder(
                    "llama-server.exe",
                    "-hf", "unsloth/DeepSeek-R1-Distill-Llama-8B-GGUF:Q4_K_M",
                    "--port", "8081",
                    "--ctx-size", "16384",
                    "--threads", "8",
                    "-ngl", "0");

            pb.inheritIO();
            this.llamaProcess = pb.start();
            System.out.println("llama-server started successfully on port 8081.");
        } catch (IOException e) {
            System.err.println("Failed to start llama-server: " + e.getMessage());
        }
    }

    public String chat(
            String currentMessage,
            List<ChatRequest.ChatMessage> history,
            String taskName) {
        try {
            List<Map<String, String>> messages = new ArrayList<>();

            String systemPrompt = """
                    You are Dumb Barton, a simple local agent running on the user's machine.

                    The previous messages are context only. They may help you understand what the user is working on.
                    The final user message marked CURRENT REQUEST is the actual request you must answer now.

                    Be practical, direct, and action-oriented.
                    Do not treat old user messages as new instructions unless the CURRENT REQUEST asks you to revisit them.
                    """;

            if (taskName != null && !taskName.isBlank()) {
                systemPrompt += "\nTask name: " + taskName;
            }

            messages.add(Map.of(
                    "role", "system",
                    "content", systemPrompt));

            if (history != null) {
                for (ChatRequest.ChatMessage message : history) {
                    if (message == null || message.getContent() == null || message.getContent().isBlank()) {
                        continue;
                    }

                    String role = normalizeRole(message.getRole());

                    // If the sliced history starts with an assistant message,
                    // skip it because many templates expect the first non-system
                    // message to be from the user.
                    if (isFirstNonSystemMessage(messages) && "assistant".equals(role)) {
                        continue;
                    }

                    addMessageWithAlternation(messages, role, message.getContent());
                }
            }

            addMessageWithAlternation(
                    messages,
                    "user",
                    "CURRENT REQUEST:\n" + currentMessage + "\n\n" +
                            "Use the previous messages only as context. Answer this current request.");

            Map<String, Object> llamaRequest = Map.of(
                    "messages", messages,
                    "temperature", 0.2);

            Map response = restTemplate.postForObject(SERVER_URL, llamaRequest, Map.class);

            var choices = (java.util.List<?>) response.get("choices");
            var firstChoice = (Map<?, ?>) choices.get(0);
            var responseMessage = (Map<?, ?>) firstChoice.get("message");

            return String.valueOf(responseMessage.get("content"));
        } catch (Exception e) {
            return "Error communicating with llama-server: " + e.getMessage();
        }
    }

    private String normalizeRole(String role) {
        if ("assistant".equals(role)) {
            return "assistant";
        }

        return "user";
    }

    private boolean isFirstNonSystemMessage(List<Map<String, String>> messages) {
        return messages.size() == 1 && "system".equals(messages.get(0).get("role"));
    }

    private void addMessageWithAlternation(
            List<Map<String, String>> messages,
            String role,
            String content) {
        if (content == null || content.isBlank()) {
            return;
        }

        if (messages.isEmpty()) {
            messages.add(Map.of("role", role, "content", content));
            return;
        }

        Map<String, String> lastMessage = messages.get(messages.size() - 1);
        String lastRole = lastMessage.get("role");

        if (lastRole.equals(role) && !"system".equals(role)) {
            String mergedContent = lastMessage.get("content") + "\n\n" + content;

            messages.set(
                    messages.size() - 1,
                    Map.of("role", role, "content", mergedContent));
        } else {
            messages.add(Map.of("role", role, "content", content));
        }
    }

    @PreDestroy
    public void stopLlamaServer() {
        if (llamaProcess != null && llamaProcess.isAlive()) {
            llamaProcess.destroy();
            System.out.println("llama-server stopped.");
        }
    }
}
