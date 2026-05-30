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
        if (request.getCurrentMessage() == null || request.getCurrentMessage().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(
                    new ChatResponse("currentMessage parameter is required", request.getConversationSummary()));
        }

        log.info(
                "Processing agent request. taskId={}, taskName={}, currentMessageLength={}, summaryLength={}",
                request.getTaskId(),
                request.getTaskName(),
                request.getCurrentMessage().length(),
                request.getConversationSummary() == null ? 0 : request.getConversationSummary().length());

        ChatResponse response = llamaServer.chat(request);

        return ResponseEntity.ok(response);
    }
}
