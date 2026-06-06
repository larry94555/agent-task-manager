package com.example.simpleagent.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ChatController {
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final AgentLoopService agentLoopService;

    public ChatController(AgentLoopService agentLoopService) {
        this.agentLoopService = agentLoopService;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        if (request == null || request.getLatest() == null || request.getLatest().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(
                    new ChatResponse("latest parameter is required")
            );
        }

        log.info(
                "Processing agent request through AgentLoopService. taskId={}, contextSize={}, latestLength={}",
                request.getTaskId(),
                request.getContext() == null ? 0 : request.getContext().size(),
                request.getLatest().length()
        );

        return ResponseEntity.ok(agentLoopService.run(request));
    }
}
