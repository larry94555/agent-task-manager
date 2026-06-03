package com.example.simpleagent.demo;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AgentActionExecutor {
    private static final int MAX_NOTE_LENGTH = 2_000;

    private final TaskConversationStore conversationStore;

    public AgentActionExecutor(TaskConversationStore conversationStore) {
        this.conversationStore = conversationStore;
    }

    public String renderAvailableActions() {
        return """
                Available actions:
                1. remember_note
                   Purpose: Save a short note that should influence later turns in this same task.
                   Input: {"note": "text to remember"}
                   Use when: the user gives a standing instruction, alias, preference, constraint, name, decision, or important technical fact.

                2. list_notes
                   Purpose: Retrieve the saved notes for this task.
                   Input: {}
                   Use when: the user asks what you remember, or when saved notes are relevant.
                """;
    }

    public ActionExecutionResult execute(ChatRequest chatRequest, AgentActionRequest actionRequest) {
        if (actionRequest == null || !actionRequest.isAction()) {
            return ActionExecutionResult.failure("Invalid action request.");
        }

        String action = clean(actionRequest.getAction());
        if (action.isBlank()) {
            return ActionExecutionResult.failure("Action name is required.");
        }

        return switch (action) {
            case "remember_note" -> rememberNote(chatRequest, actionRequest.getInput());
            case "list_notes" -> listNotes(chatRequest);
            default -> ActionExecutionResult.failure("Unknown or disallowed action: " + action);
        };
    }

    private ActionExecutionResult rememberNote(ChatRequest chatRequest, Map<String, Object> input) {
        String note = clean(String.valueOf(input.getOrDefault("note", "")));

        if (note.isBlank()) {
            return ActionExecutionResult.failure("remember_note requires input.note.");
        }

        if (note.length() > MAX_NOTE_LENGTH) {
            return ActionExecutionResult.failure("remember_note input.note is too long. Keep notes under " + MAX_NOTE_LENGTH + " characters.");
        }

        String observation = conversationStore.appendNote(chatRequest, note);
        return ActionExecutionResult.success(observation);
    }

    private ActionExecutionResult listNotes(ChatRequest chatRequest) {
        String notes = conversationStore.renderNotes(chatRequest);
        return ActionExecutionResult.success("Current task notes:\n" + notes);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
