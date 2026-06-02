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
                    "-hf", "Qwen/Qwen2.5-Coder-14B-Instruct-GGUF:Q4_K_M",
                    "--port", "8081",
                    "--ctx-size", "16384",
                    "--threads", "8",
                    "-ngl", "0",
                    "--jinja");

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
                        You are a helpful assistant who will answer questions, take instructoins, set goals, and engage in small talk.

                        The following will be an exercise to test out the logic of your local LLM.

                        You will be given a list of statmeents that will either be "user:" or "agent:".  The last one on the list will always be "User:" and this will be the task that you focus on.  You can ignore the number and focus only on what was said.

                        When you are given a prompt, you will decide if this the user has given you a question, given you an instruction, given you a goal, or has engaged in small talk.  This will be the first setnence of your response.

                        If the user asks you a question, answer the question as you would the user.  If the user asked "are you ok", then you would answer such as "I am ...." with however you are.

                        If the user gives you an instruction, such as, your name is Fred, then you would reply.  "Got it.  My name is Fred." or whatever instruction is given.

                        If the user gives you a goal such as "Create an application that says Hello." then either do the goal such as "Here is the application" or say what you must do first.  "I will need to plan for that goal.  Let me think it through...."

                        If the user egnages in small talk, then respond with engaging small talk so that is appropriate to the context of the conversation.

                        The user will provide a list of statements that will be labeled "user: " and "agent: ".  The "agent: " are statements that you can assume that you have said.  Keep this in mind when you are answering a question or engaging in small talk or resolving an ambiguity.

                        So, here's an example of what you will say.

                        1.  The latest message was "What is your name."
                        2.  This is a question.
                        3.  Here's my answer:  "I don't have a name" or "my name is Gemma" or if in the list you have been instructed to have a name, then you can say: "My name is ..." and answer as you were instructed.

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
