package com.example.simpleagent.demo;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class AgentActionExecutor {
    private static final int MAX_NOTE_LENGTH = 2_000;
    private static final int MAX_TASK_NAME_LENGTH = 120;

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

                3. create_task
                   Purpose: Ask the frontend to create a new child task under the current task.
                   Input: {"name": "task name", "switchToTask": false}
                   Use when: the user asks you to create, split out, track, or start a separate task.

                4. rename_task
                   Purpose: Ask the frontend to rename a task.
                   Input: {"taskId": 123, "name": "new task name"}
                   If taskId is omitted, the current task is renamed.

                5. close_task
                   Purpose: Ask the frontend to mark a task as closed/archived. This does not delete the task.
                   Input: {"taskId": 123}
                   If taskId is omitted, the current task is closed.

                6. list_tasks
                   Purpose: List the current frontend tasks known to the backend from the request snapshot.
                   Input: {}
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
            case "create_task" -> createTask(chatRequest, actionRequest.getInput());
            case "rename_task" -> renameTask(chatRequest, actionRequest.getInput());
            case "close_task" -> closeTask(chatRequest, actionRequest.getInput());
            case "list_tasks" -> listTasks(chatRequest);
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

    private ActionExecutionResult createTask(ChatRequest chatRequest, Map<String, Object> input) {
        String name = clean(String.valueOf(input.getOrDefault("name", "")));
        if (name.isBlank()) {
            return ActionExecutionResult.failure("create_task requires input.name.");
        }

        if (name.length() > MAX_TASK_NAME_LENGTH) {
            return ActionExecutionResult.failure("create_task input.name is too long. Keep names under " + MAX_TASK_NAME_LENGTH + " characters.");
        }

        if (taskNameExists(chatRequest, name)) {
            return ActionExecutionResult.failure("A task named \"" + name + "\" already exists. Choose a more specific name.");
        }

        boolean switchToTask = Boolean.TRUE.equals(input.get("switchToTask"));
        ClientTaskAction taskAction = ClientTaskAction.createTask(name, chatRequest.getTaskId(), switchToTask);
        return ActionExecutionResult.success(
                "Requested creation of child task \"" + name + "\" under current task " + chatRequest.getTaskId() + ".",
                taskAction
        );
    }

    private ActionExecutionResult renameTask(ChatRequest chatRequest, Map<String, Object> input) {
        Integer taskId = integerInput(input, "taskId").orElse(chatRequest.getTaskId());
        if (taskId == null) {
            return ActionExecutionResult.failure("rename_task requires input.taskId when there is no current task.");
        }

        if (!taskExists(chatRequest, taskId)) {
            return ActionExecutionResult.failure("Cannot rename task " + taskId + " because it is not in the current task snapshot.");
        }

        String name = clean(String.valueOf(input.getOrDefault("name", "")));
        if (name.isBlank()) {
            return ActionExecutionResult.failure("rename_task requires input.name.");
        }

        if (name.length() > MAX_TASK_NAME_LENGTH) {
            return ActionExecutionResult.failure("rename_task input.name is too long. Keep names under " + MAX_TASK_NAME_LENGTH + " characters.");
        }

        if (taskNameExistsForOtherTask(chatRequest, taskId, name)) {
            return ActionExecutionResult.failure("Another task named \"" + name + "\" already exists. Choose a different name.");
        }

        ClientTaskAction taskAction = ClientTaskAction.renameTask(taskId, name);
        return ActionExecutionResult.success("Requested rename of task " + taskId + " to \"" + name + "\".", taskAction);
    }

    private ActionExecutionResult closeTask(ChatRequest chatRequest, Map<String, Object> input) {
        Integer taskId = integerInput(input, "taskId").orElse(chatRequest.getTaskId());
        if (taskId == null) {
            return ActionExecutionResult.failure("close_task requires input.taskId when there is no current task.");
        }

        if (!taskExists(chatRequest, taskId)) {
            return ActionExecutionResult.failure("Cannot close task " + taskId + " because it is not in the current task snapshot.");
        }

        ClientTaskAction taskAction = ClientTaskAction.closeTask(taskId);
        return ActionExecutionResult.success("Requested close/archive of task " + taskId + ".", taskAction);
    }

    private ActionExecutionResult listTasks(ChatRequest chatRequest) {
        List<TaskSnapshot> tasks = chatRequest.getTasks();
        if (tasks == null || tasks.isEmpty()) {
            return ActionExecutionResult.success("No frontend tasks were provided in the request snapshot.");
        }

        String rendered = tasks.stream()
                .map(task -> "- id=" + task.getId()
                        + ", name=\"" + clean(task.getName()) + "\""
                        + ", status=" + clean(task.getStatus())
                        + ", lifecycle=" + clean(task.getLifecycle())
                        + ", parentTaskId=" + task.getParentTaskId()
                        + ", createdBy=" + clean(task.getCreatedBy()))
                .collect(Collectors.joining("\n"));

        return ActionExecutionResult.success("Current frontend tasks:\n" + rendered);
    }

    private boolean taskExists(ChatRequest request, Integer taskId) {
        if (request.getTasks() == null || taskId == null) {
            return false;
        }

        return request.getTasks().stream().anyMatch(task -> taskId.equals(task.getId()));
    }

    private boolean taskNameExists(ChatRequest request, String name) {
        if (request.getTasks() == null) {
            return false;
        }

        String normalized = name.trim().toLowerCase();
        return request.getTasks().stream()
                .anyMatch(task -> clean(task.getName()).toLowerCase().equals(normalized));
    }

    private boolean taskNameExistsForOtherTask(ChatRequest request, Integer taskId, String name) {
        if (request.getTasks() == null) {
            return false;
        }

        String normalized = name.trim().toLowerCase();
        return request.getTasks().stream()
                .filter(task -> !taskId.equals(task.getId()))
                .anyMatch(task -> clean(task.getName()).toLowerCase().equals(normalized));
    }

    private Optional<Integer> integerInput(Map<String, Object> input, String key) {
        Object value = input.get(key);
        if (value == null) {
            return Optional.empty();
        }

        if (value instanceof Number number) {
            return Optional.of(number.intValue());
        }

        try {
            return Optional.of(Integer.parseInt(String.valueOf(value)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}

