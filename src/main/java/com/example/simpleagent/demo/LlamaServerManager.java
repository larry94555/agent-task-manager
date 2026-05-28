package com.example.simpleagent.demo;

import org.springframework.stereotype.Component;

@Component
public class LlamaServerManager {

    public String chat(String text) {
        // Implement the logic to send the text to llama-server and get the response
        // This is a placeholder implementation
        return "Response from llama-server for input: " + text;
    }


}
