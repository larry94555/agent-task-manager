package com.example.simpleagent.demo;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.util.ArrayList;
import java.util.Arrays;
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

                        The latest message will tell you how to responsd and the context will provide
                        additional details.

                        In the latest message, you will either be given a question, a command, or given small talk.

                        If latest message is a command, acknowledge that you understand and state what you will do or
                        what you accept.  If the latest messages is a question, do your best to answer it in light of
                        what's been discussed, previous commands, or what's true.  For a question, make sure to provide an answer in the immeidate response.  If the latest message is
                        small talk, then response with small talk that is socially appropriate and takes
                        into account what has been said in the context.

                        In responses, try not to repeat but make sure to answer the question if possible.

                        Make sure to prioritize the last commands over earlier commands.  The state may change because
                        of information provided later that overrides the original information.  In this case, prioritize the later message over the earlier message.

                        Answer the the latest message from the user but take into account the full history.
                        As much as possible, take any assertions as the given for the response.

                        Once you agree to something, then continue to agree and be consistent with previous
                        statements that have been made.

                        In replying, try to make a statement that either answers the
                        question or do your best to provide an answer that takes into account
                        the context provided. If you don't know the answer, say you don't know.

                        """));

        // Build a single user-turn message containing the full history + latest
        StringBuilder conversation = new StringBuilder();

        List<String> context = request.getContext();
        int step = 1;
        if (context != null && !context.isEmpty()) {
            // conversation.append("Conversation so far:\n");
            for (String line : context) {
                // conversation.append(line).append("\n");
                conversation.append(step).append(") ").append(line).append("\n");
                step++;
            }
        }
        conversation.append(step).append(") ").append(request.getLatest()).append("\n");

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
