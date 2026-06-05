package com.example.simpleagent.demo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
                    "--jinja");

            pb.inheritIO();
            this.llamaProcess = pb.start();
            System.out.println("llama-server started successfully on port 8081.");
        } catch (IOException e) {
            System.err.println("Failed to start llama-server: " + e.getMessage());
        }
    }

    public String complete(List<AgentMessage> messages, double temperature, int maxTokens) {
        return completeWithTrace(messages, temperature, maxTokens).getContent();
    }

    public LlamaCompletionResult completeWithTrace(List<AgentMessage> messages, double temperature, int maxTokens) {
        ModelCallTrace trace = new ModelCallTrace();
        long startedNanos = System.nanoTime();

        try {
            Map<String, Object> llamaRequest = buildLlamaRequest(messages, temperature, maxTokens);
            String requestBody = objectMapper.writeValueAsString(llamaRequest);

            trace.markStarted(temperature, maxTokens, messages == null ? 0 : messages.size(), requestBody);
            logLlamaRequest(requestBody);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<String> responseEntity = restTemplate.postForEntity(
                    SERVER_URL,
                    new HttpEntity<>(requestBody, headers),
                    String.class
            );

            String rawResponseBody = responseEntity.getBody();
            Map<String, Object> response = objectMapper.readValue(
                    rawResponseBody == null ? "{}" : rawResponseBody,
                    new TypeReference<>() {}
            );

            String content = extractMessageContent(response);
            trace.markSuccess(
                    startedNanos,
                    responseEntity.getStatusCode().value(),
                    rawResponseBody,
                    content
            );

            return new LlamaCompletionResult(content, trace);
        } catch (Exception e) {
            trace.markFailure(startedNanos, e.getMessage());
            throw new LlamaCompletionException("llama.cpp call failed: " + e.getMessage(), trace, e);
        }
    }

    private Map<String, Object> buildLlamaRequest(List<AgentMessage> messages, double temperature, int maxTokens) {
        List<Map<String, Object>> apiMessages = new ArrayList<>();

        if (messages != null) {
            for (AgentMessage message : messages) {
                Map<String, Object> apiMessage = new LinkedHashMap<>();
                apiMessage.put("role", message.getRole());
                apiMessage.put("content", message.getContent());
                apiMessages.add(apiMessage);
            }
        }

        Map<String, Object> llamaRequest = new LinkedHashMap<>();
        llamaRequest.put("messages", apiMessages);
        llamaRequest.put("temperature", temperature);
        llamaRequest.put("max_tokens", maxTokens);
        return llamaRequest;
    }

    private void logLlamaRequest(String requestBody) {
        try {
            Object parsed = objectMapper.readValue(requestBody, Object.class);
            String prettyRequest = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed);
            logger.info("llamaRequest: " + System.lineSeparator() + prettyRequest);
        } catch (JsonProcessingException e) {
            logger.warning("Failed to serialize llamaRequest: " + e.getMessage());
        }
    }

    private String extractMessageContent(Map<String, Object> response) {
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
