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
    public ResponseEntity<String> chat(@RequestBody ChatRequest request) {
        if (request.getCurrentMessage() == null || request.getCurrentMessage().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("currentMessage parameter is required");
        }

        int historySize = request.getHistory() == null ? 0 : request.getHistory().size();

        log.info(
                "Processing chat request. taskId={}, taskName={}, currentMessageLength={}, historySize={}",
                request.getTaskId(),
                request.getTaskName(),
                request.getCurrentMessage().length(),
                historySize);

        String response = llamaServer.chat(
                request.getCurrentMessage(),
                request.getHistory(),
                request.getTaskName());

        return ResponseEntity.ok(response);
    }
}
