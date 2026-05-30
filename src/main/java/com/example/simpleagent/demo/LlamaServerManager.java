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

            messages.add(Map.of(
                    "role", "system",
                    "content",
                    """
                            You are Dumb Barton, a simple local agent running on the user's machine.

                            The previous messages are context only. They may help you understand what the user is working on.
                            The final user message marked CURRENT REQUEST is the actual request you must answer now.

                            Be practical, direct, and action-oriented.
                            Do not treat old user messages as new instructions unless the CURRENT REQUEST asks you to revisit them.
                            """));

            if (taskName != null && !taskName.isBlank()) {
                messages.add(Map.of(
                        "role", "user",
                        "content", "Task name/context: " + taskName));
            }

            if (history != null) {
                for (ChatRequest.ChatMessage message : history) {
                    if (message == null || message.getContent() == null || message.getContent().isBlank()) {
                        continue;
                    }

                    String role = normalizeRole(message.getRole());

                    messages.add(Map.of(
                            "role", role,
                            "content", message.getContent()));
                }
            }

            messages.add(Map.of(
                    "role", "user",
                    "content",
                    "CURRENT REQUEST:\n" + currentMessage + "\n\n" +
                            "Use the previous messages only as context. Answer this current request."));

            Map<String, Object> request = Map.of(
                    "messages", messages);

            Map response = restTemplate.postForObject(SERVER_URL, request, Map.class);

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

    @PreDestroy
    public void stopLlamaServer() {
        if (llamaProcess != null && llamaProcess.isAlive()) {
            llamaProcess.destroy();
            System.out.println("llama-server stopped.");
        }
    }
}
