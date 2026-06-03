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

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        if (request.getLatest() == null || request.getLatest().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(
                    new ChatResponse("latest parameter is required"));
        }

        log.info("Processing agent request. taskId={}, contextSize={}, latestLength={}",
                request.getTaskId(),
                request.getContext() == null ? 0 : request.getContext().size(),
                request.getLatest() == null ? 0 : request.getLatest().length());

        ChatResponse response = llamaServer.chat(request);

        return ResponseEntity.ok(response);
    }
}
