package com.example.simpleagent.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    @Autowired
    private LlamaServerManager llamaServer;

     //Start llama-server manually: llama-server -m /path/to/model.gguf --host 127.0.0.1 --port 8081
    @PostMapping("/chat")
    public ResponseEntity<String> chat(@RequestBody ChatRequest request) {
        if (request.getText() == null || request.getText().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Text parameter is required");
        }

        log.info("Processing chat request with text length: {}", request.getText().length());
        String response = llamaServer.chat(request.getText());
        return ResponseEntity.ok(response);
       
    }
}


