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

    public ChatResponse chat(ChatRequest request) {
        try {
            String answer = generateAnswer(request);
            String updatedSummary = updateSummary(request, answer);

            return new ChatResponse(answer, updatedSummary);
        } catch (Exception e) {
            return new ChatResponse(
                    "Error communicating with llama-server: " + e.getMessage(),
                    request.getConversationSummary());
        }
    }

    private String generateAnswer(ChatRequest request) {
        List<Map<String, String>> messages = new ArrayList<>();

        String systemPrompt = """
                You are Dumb Barton, a simple local agent running on the user's machine.

                The conversation summary is context only.
                The final user message marked CURRENT REQUEST is the actual request you must answer now.

                Be practical, direct, and action-oriented.
                Do not treat old messages as new instructions unless the CURRENT REQUEST asks you to revisit them.
                """;

        if (request.getTaskName() != null && !request.getTaskName().isBlank()) {
            systemPrompt += "\nTask name: " + request.getTaskName();
        }

        if (request.getConversationSummary() != null && !request.getConversationSummary().isBlank()) {
            systemPrompt += "\n\nConversation summary. This is context only:\n"
                    + request.getConversationSummary();
        }

        messages.add(Map.of(
                "role",
                "user",
                "content",
                "CURRENT REQUEST:\n" + request.getCurrentMessage() + "\n\n"
                        + "Use the conversation summary only as background context. Answer this current request."));

        Map<String, Object> llamaRequest = Map.of(
                "messages", messages,
                "temperature", 0.2);

        Map response = restTemplate.postForObject(SERVER_URL, llamaRequest, Map.class);

        return extractMessageContent(response);
    }

    private String updateSummary(ChatRequest request, String answer) {
        List<Map<String, String>> messages = new ArrayList<>();

        messages.add(Map.of(
                "role", "system",
                "content",
                """
                        You maintain a compact rolling summary for a local agent task.

                        Update the summary using the old summary, the user's latest request, and the agent's latest response.

                        Preserve:
                        - the user's goal
                        - important decisions
                        - constraints
                        - file names, commands, URLs, ports, branches, and technical details
                        - unresolved problems
                        - next steps

                        Remove:
                        - small talk
                        - redundant phrasing
                        - obsolete details
                        - unnecessary wording

                        Keep the summary concise but useful. Do not answer the user.
                        Return only the updated summary.
                        """));

        String oldSummary = request.getConversationSummary() == null
                ? ""
                : request.getConversationSummary();

        String summaryInput = "OLD SUMMARY:\n" + oldSummary + "\n\n"
                + "LATEST USER REQUEST:\n" + request.getCurrentMessage() + "\n\n"
                + "LATEST AGENT RESPONSE:\n" + answer + "\n\n"
                + "Return the updated rolling summary.";

        messages.add(Map.of(
                "role", "user",
                "content", summaryInput));

        Map<String, Object> llamaRequest = Map.of(
                "messages", messages,
                "temperature", 0.1,
                "max_tokens", 800);

        Map response = restTemplate.postForObject(SERVER_URL, llamaRequest, Map.class);

        return extractMessageContent(response);
    }

    private String extractMessageContent(Map response) {
        var choices = (java.util.List<?>) response.get("choices");
        var firstChoice = (Map<?, ?>) choices.get(0);
        var responseMessage = (Map<?, ?>) firstChoice.get("message");

        return String.valueOf(responseMessage.get("content"));
    }

    @PreDestroy
    public void stopLlamaServer() {
        if (llamaProcess != null && llamaProcess.isAlive()) {
            llamaProcess.destroy();
            System.out.println("llama-server stopped.");
        }
    }
}
