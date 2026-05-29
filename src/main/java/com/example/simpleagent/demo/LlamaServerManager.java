package com.example.simpleagent.demo;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

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
                "-ngl", "0"
            );
            
            pb.inheritIO(); 
            this.llamaProcess = pb.start();
            System.out.println("llama-server started successfully on port 8081.");
        } catch (IOException e) {
            System.err.println("Failed to start llama-server: " + e.getMessage());
        }
    }

    public String chat(String text) {
        try {
            Map<String, Object> request = Map.of(
                "messages", new Object[]{
                    Map.of("role", "user", "content", text)
                }
            );

            Map<String, Object> response = restTemplate.postForObject(SERVER_URL, request, Map.class);
            
            var choices = (java.util.List<Map<String, Object>>) response.get("choices");
            var message = (Map<String, String>) choices.get(0).get("message");
            return message.get("content");
            
        } catch (Exception e) {
            return "Error communicating with llama-server: " + e.getMessage();
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
