package com.example.simpleagent.demo;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AgentLoopService {
    private static final int MAX_ACTION_STEPS = 4;

    private final LlamaServerManager llamaServer;
    private final AgentActionExecutor actionExecutor;
    private final TaskConversationStore conversationStore;
    private final ObjectMapper objectMapper;

    public AgentLoopService(
            LlamaServerManager llamaServer,
            AgentActionExecutor actionExecutor,
            TaskConversationStore conversationStore
    ) {
        this.llamaServer = llamaServer;
        this.actionExecutor = actionExecutor;
        this.conversationStore = conversationStore;
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public ChatResponse run(ChatRequest request) {
        try {
            List<AgentMessage> loopMessages = buildInitialMessages(request);
            List<String> actionTrace = new ArrayList<>();

            for (int step = 1; step <= MAX_ACTION_STEPS; step++) {
                String rawModelOutput = cleanModelText(llamaServer.complete(loopMessages, 0.2, 1_200));
                AgentActionRequest decision = parseDecision(rawModelOutput);

                if (decision == null) {
                    return finish(request, rawModelOutput, actionTrace);
                }

                if (decision.isFinal()) {
                    String finalAnswer = clean(decision.getContent());
                    if (finalAnswer.isBlank()) {
                        finalAnswer = "I completed the request, but the model returned an empty final answer.";
                    }

                    return finish(request, finalAnswer, actionTrace);
                }

                if (decision.isAction()) {
                    ActionExecutionResult result = actionExecutor.execute(request, decision);
                    String traceLine = formatActionTrace(step, decision, result);
                    actionTrace.add(traceLine);

                    loopMessages.add(AgentMessage.assistant(rawModelOutput));
                    loopMessages.add(AgentMessage.user("""
                            ACTION RESULT:
                            %s

                            Continue the agent loop. Return exactly one JSON object.
                            If you have enough information to answer the user, return:
                            {"type":"final","content":"your answer"}
                            """.formatted(result.getObservation())));

                    continue;
                }

                return finish(request, rawModelOutput, actionTrace);
            }

            String finalAnswer = "I hit the maximum number of action steps before finishing. Here is what happened:\n"
                    + String.join("\n", actionTrace);

            return finish(request, finalAnswer, actionTrace);
        } catch (Exception e) {
            return new ChatResponse("Error running agent loop: " + e.getMessage());
        }
    }

    private List<AgentMessage> buildInitialMessages(ChatRequest request) {
        List<AgentMessage> messages = new ArrayList<>();

        messages.add(AgentMessage.system(buildSystemPrompt(request)));
        messages.addAll(conversationStore.loadHistory(request));
        messages.add(AgentMessage.user("""
                CURRENT REQUEST:
                %s

                Return exactly one JSON object using the required protocol.
                """.formatted(request.getLatest())));

        return messages;
    }

    private String buildSystemPrompt(ChatRequest request) {
        return """
                You are Dumb Barton, a local task agent running on the user's machine.

                You are not just a chatbot. You run an agent loop:
                1. Understand the current request.
                2. Use task context and previous messages.
                3. Decide whether to answer directly or call one safe action.
                4. If you call an action, wait for the action result before giving the final answer.

                Task ID:
                %s

                Conversation context from frontend:
                %s

                Backend task notes:
                %s

                %s

                Required output protocol:
                Return exactly one JSON object and nothing else.

                To answer the user directly:
                {"type":"final","content":"your user-facing answer"}

                To call an action:
                {"type":"action","action":"remember_note","input":{"note":"important note"}}

                or:
                {"type":"action","action":"list_notes","input":{}}

                Rules:
                - Do not wrap the JSON in Markdown.
                - Do not include commentary before or after the JSON.
                - Do not invent action names.
                - Do not call actions unnecessarily.
                - Use remember_note when the user gives a name, alias, standing instruction, preference, durable fact, technical constraint, or important decision that should affect later turns.
                - Use prior conversation messages and task memory when answering follow-up questions.
                - Be practical, direct, and concise.
                """.formatted(
                request.getTaskId() == null ? "(none)" : request.getTaskId(),
                renderContext(request),
                conversationStore.renderNotes(request),
                actionExecutor.renderAvailableActions()
        );
    }

    private String renderContext(ChatRequest request) {
        if (request.getContext() == null || request.getContext().isEmpty()) {
            return "(none)";
        }

        StringBuilder sb = new StringBuilder();
        int step = 1;

        for (Object line : request.getContext()) {
            if (line != null) {
                sb.append(step).append(") ").append(line).append("\n");
                step++;
            }
        }

        return sb.toString().trim();
    }

    private ChatResponse finish(ChatRequest request, String finalAnswer, List<String> actionTrace) {
        String cleanedAnswer = cleanModelText(finalAnswer);
        conversationStore.appendUserAssistantTurn(request, request.getLatest(), cleanedAnswer);
        return new ChatResponse(cleanedAnswer);
    }

    private AgentActionRequest parseDecision(String rawModelOutput) {
        String json = extractFirstJsonObject(cleanModelText(rawModelOutput));
        if (json == null) {
            return null;
        }

        try {
            return objectMapper.readValue(json, AgentActionRequest.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String extractFirstJsonObject(String text) {
        if (text == null) {
            return null;
        }

        int start = text.indexOf('{');
        if (start < 0) {
            return null;
        }

        boolean inString = false;
        boolean escaped = false;
        int depth = 0;

        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (inString && c == '\\') {
                escaped = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (!inString && c == '{') {
                depth++;
            }

            if (!inString && c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }

        return null;
    }

    private String formatActionTrace(int step, AgentActionRequest decision, ActionExecutionResult result) {
        return "step=" + step
                + ", action=" + decision.getAction()
                + ", success=" + result.isSuccess()
                + ", observation=" + result.getObservation();
    }

    private String cleanModelText(String value) {
        if (value == null) {
            return "";
        }

        String cleaned = value
                .replaceAll("(?is)<think>.*?</think>", "")
                .trim();

        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring("```json".length()).trim();
        }

        if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring("```".length()).trim();
        }

        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3).trim();
        }

        return cleaned;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
