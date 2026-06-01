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
            return new ChatResponse(answer);
        } catch (Exception e) {
            return new ChatResponse("Error communicating with llama-server: " + e.getMessage());
        }
    }

    private String generateAnswer(ChatRequest request) {
        List<Map<String, String>> messages = new ArrayList<>();

        messages.add(Map.of(
                "role", "system",
                "content",
                """
                        You are a helpful assistant in an ongoing conversation.
                        The conversation history is provided as a series of lines,
                        each prefixed with either "user: " or "agent: ".
                        Read the full history to understand the context, then answer
                        the latest user message. Base your answer on what the user
                        expects to hear given everything said so far.
                        Reply with only your answer, no preamble.
                        """));

        // Build a single user-turn message containing the full history + latest
        StringBuilder conversation = new StringBuilder();

        List<String> context = request.getContext();
        if (context != null && !context.isEmpty()) {
            conversation.append("Conversation so far:\n");
            for (String line : context) {
                conversation.append(line).append("\n");
            }
            conversation.append("\n");
        }

        conversation.append("Latest user message:\n");
        conversation.append("user: ").append(request.getLatest());

        messages.add(Map.of(
                "role", "user",
                "content", conversation.toString()));

        Map<String, Object> llamaRequest = Map.of(
                "messages", messages,
                "temperature", 0.0);

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
