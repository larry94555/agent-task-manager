package com.example.simpleagent.demo;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class TaskConversationStore {
    private static final int MAX_HISTORY_MESSAGES = 24;
    private static final int MAX_NOTES_PER_TASK = 100;

    private final ConcurrentMap<String, List<AgentMessage>> historyByTask = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, List<String>> notesByTask = new ConcurrentHashMap<>();

    public List<AgentMessage> loadHistory(ChatRequest request) {
        List<AgentMessage> history = historyByTask.get(taskKey(request));
        if (history == null) {
            return List.of();
        }

        synchronized (history) {
            return new ArrayList<>(history);
        }
    }

    public void appendUserAssistantTurn(ChatRequest request, String userMessage, String assistantMessage) {
        String key = taskKey(request);
        List<AgentMessage> history = historyByTask.computeIfAbsent(
                key,
                ignored -> Collections.synchronizedList(new ArrayList<>())
        );

        synchronized (history) {
            history.add(AgentMessage.user(userMessage));
            history.add(AgentMessage.assistant(assistantMessage));

            while (history.size() > MAX_HISTORY_MESSAGES) {
                history.remove(0);
            }
        }
    }

    public String appendNote(ChatRequest request, String note) {
        String cleaned = cleanText(note);
        if (cleaned.isBlank()) {
            return "No note was saved because the note was blank.";
        }

        String key = taskKey(request);
        List<String> notes = notesByTask.computeIfAbsent(
                key,
                ignored -> Collections.synchronizedList(new ArrayList<>())
        );

        synchronized (notes) {
            if (notes.size() >= MAX_NOTES_PER_TASK) {
                notes.remove(0);
            }

            notes.add(cleaned);
            return "Saved task note #" + notes.size() + ": " + cleaned;
        }
    }

    public List<String> listNotes(ChatRequest request) {
        List<String> notes = notesByTask.get(taskKey(request));
        if (notes == null) {
            return List.of();
        }

        synchronized (notes) {
            return new ArrayList<>(notes);
        }
    }

    public String renderNotes(ChatRequest request) {
        List<String> notes = listNotes(request);
        if (notes.isEmpty()) {
            return "(none)";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < notes.size(); i++) {
            sb.append(i + 1).append(". ").append(notes.get(i)).append("\n");
        }
        return sb.toString().trim();
    }

    private String taskKey(ChatRequest request) {
        if (request.getTaskId() != null) {
            return "task-id:" + request.getTaskId();
        }

        return "anonymous-task";
    }

    private String cleanText(String value) {
        return value == null ? "" : value.trim();
    }
}
