package com.example.simpleagent.demo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@Component
public class LlamaServerManager {
    private static final Logger logger = Logger.getLogger(LlamaServerManager.class.getName());
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SERVER_URL = "http://localhost:8081/v1/chat/completions";

    private Process llamaProcess;
    private final RestTemplate restTemplate = new RestTemplate();

    @PostConstruct
    public void startLlamaServer() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "llama-server.exe",
                    "-hf", "Qwen/Qwen2.5-Coder-14B-Instruct-GGUF:Q4_K_M",
                    "--host", "0.0.0.0",
                    "--port", "8081",
                    "--ctx-size", "32768",
                    "--threads", "8",
                    "-ngl", "0",
                    "--alias", "qwen2.5-coder-14b",
                    "--jinja"
            );

            pb.inheritIO();
            this.llamaProcess = pb.start();
            System.out.println("llama-server started successfully on port 8081.");
        } catch (IOException e) {
            System.err.println("Failed to start llama-server: " + e.getMessage());
        }
    }

    public String complete(List<AgentMessage> messages, double temperature, int maxTokens) {
        List<Map<String, String>> apiMessages = new ArrayList<>();

        for (AgentMessage message : messages) {
            Map<String, String> apiMessage = new LinkedHashMap<>();
            apiMessage.put("role", message.getRole());
            apiMessage.put("content", message.getContent());
            apiMessages.add(apiMessage);
        }

        Map<String, Object> llamaRequest = new LinkedHashMap<>();
        llamaRequest.put("messages", apiMessages);
        llamaRequest.put("temperature", temperature);
        llamaRequest.put("max_tokens", maxTokens);

        logLlamaRequest(llamaRequest);

        Map<?, ?> response = restTemplate.postForObject(SERVER_URL, llamaRequest, Map.class);
        return extractMessageContent(response);
    }

    private void logLlamaRequest(Map<String, Object> llamaRequest) {
        try {
            String prettyRequest = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(llamaRequest);
            logger.info("llamaRequest: " + System.lineSeparator() + prettyRequest);
        } catch (JsonProcessingException e) {
            logger.warning("Failed to serialize llamaRequest: " + e.getMessage());
        }
    }

    private String extractMessageContent(Map<?, ?> response) {
        if (response == null) {
            throw new IllegalStateException("llama-server returned no response body.");
        }

        Object choicesObject = response.get("choices");
        if (!(choicesObject instanceof List<?> choices) || choices.isEmpty()) {
            throw new IllegalStateException("llama-server response did not contain choices.");
        }

        Object firstChoiceObject = choices.get(0);
        if (!(firstChoiceObject instanceof Map<?, ?> firstChoice)) {
            throw new IllegalStateException("llama-server choice was not an object.");
        }

        Object messageObject = firstChoice.get("message");
        if (!(messageObject instanceof Map<?, ?> responseMessage)) {
            throw new IllegalStateException("llama-server choice did not contain a message object.");
        }

        Object content = responseMessage.get("content");
        if (content == null) {
            throw new IllegalStateException("llama-server message did not contain content.");
        }

        return String.valueOf(content);
    }

    @PreDestroy
    public void stopLlamaServer() {
        if (llamaProcess != null && llamaProcess.isAlive()) {
            llamaProcess.destroy();
            System.out.println("llama-server stopped.");
        }
    }
}
