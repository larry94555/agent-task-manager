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
    private final WebReadOnlyToolService webReadOnlyToolService;
    private final FreeWebSearchService freeWebSearchService;
    private final WebToolPolicy webToolPolicy = new WebToolPolicy();

    public AgentActionExecutor(
            TaskConversationStore conversationStore,
            WebReadOnlyToolService webReadOnlyToolService,
            FreeWebSearchService freeWebSearchService
    ) {
        this.conversationStore = conversationStore;
        this.webReadOnlyToolService = webReadOnlyToolService;
        this.freeWebSearchService = freeWebSearchService;
    }

    public String renderAvailableActions() {
        return """
                Available actions:

                1. remember_note
                Purpose: Save a short note that should influence later turns in this same task.
                Input: {"note": "text to remember"}

                2. list_notes
                Purpose: Retrieve the saved notes for this task.
                Input: {}

                3. create_task
                Purpose: Ask the frontend to create a new child task under the current task.
                Input: {"name": "task name", "switchToTask": false}

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

                7. web_fetch_url
                Purpose: Fetch readable text from a public http/https URL.
                Input: {"url": "https://example.com/page", "maxChars": 12000}
                Read-only. Does not use browser cookies, user accounts, local browser history, forms, or JavaScript execution.

                8. web_page_outline
                Purpose: Fetch a public web page and return its title, metadata, and h1/h2/h3 outline.
                Input: {"url": "https://example.com/page"}

                9. web_extract_links
                Purpose: Fetch a public web page and return public http/https links found on that page.
                Input: {"url": "https://example.com/page", "sameDomainOnly": true, "maxLinks": 20}

                10. web_search
                Purpose: Search the public web and return candidate result URLs.
                Input: {"query": "search terms", "maxResults": 5}
                Free-first provider order: DuckDuckGo by default; SearXNG when WEB_SEARCH_PROVIDER=searxng or SEARXNG_BASE_URL is configured; Brave only when WEB_SEARCH_PROVIDER=brave is explicitly configured.

                11. web_research
                Purpose: Search the public web, fetch the top result pages, and return compact evidence passages with source URLs.
                Input: {"query": "research question", "maxResults": 6, "maxPagesToFetch": 3, "maxPassagesPerSource": 3}
                Use this instead of web_search when the user wants an explained answer, comparison, or researched summary rather than just a list of search results.
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
            case "web_fetch_url" -> webFetchUrl(actionRequest.getInput());
            case "web_page_outline" -> webPageOutline(actionRequest.getInput());
            case "web_extract_links" -> webExtractLinks(actionRequest.getInput());
            case "web_search" -> webSearch(actionRequest.getInput());
            case "web_research" -> webResearch(actionRequest.getInput());
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
        return ActionExecutionResult.success(conversationStore.appendNote(chatRequest, note));
    }

    private ActionExecutionResult listNotes(ChatRequest chatRequest) {
        return ActionExecutionResult.success("Current task notes:\n" + conversationStore.renderNotes(chatRequest));
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
        return ActionExecutionResult.success("Requested creation of child task \"" + name + "\" under current task " + chatRequest.getTaskId() + ".", taskAction);
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
        return ActionExecutionResult.success("Requested close/archive of task " + taskId + ".", ClientTaskAction.closeTask(taskId));
    }

    private ActionExecutionResult listTasks(ChatRequest chatRequest) {
        List<TaskSnapshot> tasks = taskSnapshots(chatRequest);
        if (tasks.isEmpty()) {
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

    private ActionExecutionResult webFetchUrl(Map<String, Object> input) {
        try {
            String url = clean(String.valueOf(input.getOrDefault("url", "")));
            int maxChars = webToolPolicy.boundedInt(input.get("maxChars"), WebToolPolicy.DEFAULT_MAX_CHARS, 1_000, WebToolPolicy.MAX_EXTRACTED_CHARS);
            return ActionExecutionResult.success(webReadOnlyToolService.webFetchUrl(url, maxChars));
        } catch (Exception e) {
            return ActionExecutionResult.failure("web_fetch_url failed: " + e.getMessage());
        }
    }

    private ActionExecutionResult webPageOutline(Map<String, Object> input) {
        try {
            return ActionExecutionResult.success(webReadOnlyToolService.webPageOutline(clean(String.valueOf(input.getOrDefault("url", "")))));
        } catch (Exception e) {
            return ActionExecutionResult.failure("web_page_outline failed: " + e.getMessage());
        }
    }

    private ActionExecutionResult webExtractLinks(Map<String, Object> input) {
        try {
            String url = clean(String.valueOf(input.getOrDefault("url", "")));
            boolean sameDomainOnly = booleanInput(input.get("sameDomainOnly"), true);
            int maxLinks = webToolPolicy.boundedInt(input.get("maxLinks"), WebToolPolicy.DEFAULT_MAX_LINKS, 1, WebToolPolicy.MAX_LINKS);
            return ActionExecutionResult.success(webReadOnlyToolService.webExtractLinks(url, sameDomainOnly, maxLinks));
        } catch (Exception e) {
            return ActionExecutionResult.failure("web_extract_links failed: " + e.getMessage());
        }
    }

    private ActionExecutionResult webSearch(Map<String, Object> input) {
        try {
            String query = clean(String.valueOf(input.getOrDefault("query", "")));
            int maxResults = webToolPolicy.boundedInt(input.get("maxResults"), WebToolPolicy.DEFAULT_SEARCH_RESULTS, 1, WebToolPolicy.MAX_SEARCH_RESULTS);
            return ActionExecutionResult.success(freeWebSearchService.webSearch(query, maxResults));
        } catch (Exception e) {
            return ActionExecutionResult.failure("web_search failed: " + e.getMessage());
        }
    }

    private ActionExecutionResult webResearch(Map<String, Object> input) {
        try {
            String query = clean(String.valueOf(input.getOrDefault("query", "")));
            int maxResults = webToolPolicy.boundedInt(input.get("maxResults"), 6, 1, WebToolPolicy.MAX_SEARCH_RESULTS);
            int maxPagesToFetch = webToolPolicy.boundedInt(input.get("maxPagesToFetch"), 3, 1, 5);
            int maxPassagesPerSource = webToolPolicy.boundedInt(input.get("maxPassagesPerSource"), 3, 1, 5);
            return ActionExecutionResult.success(freeWebSearchService.webResearch(query, maxResults, maxPagesToFetch, maxPassagesPerSource));
        } catch (Exception e) {
            return ActionExecutionResult.failure("web_research failed: " + e.getMessage());
        }
    }

    private boolean taskExists(ChatRequest request, Integer taskId) {
        return taskId != null
                && taskSnapshots(request).stream().anyMatch(task -> taskId.equals(task.getId()));
    }

    private boolean taskNameExists(ChatRequest request, String name) {
        return taskSnapshots(request).stream().anyMatch(task -> clean(task.getName()).equalsIgnoreCase(name.trim()));
    }

    private boolean taskNameExistsForOtherTask(ChatRequest request, Integer taskId, String name) {
        return taskSnapshots(request).stream()
                .filter(task -> !taskId.equals(task.getId()))
                .anyMatch(task -> clean(task.getName()).equalsIgnoreCase(name.trim()));
    }

    @SuppressWarnings("unchecked")
    private List<TaskSnapshot> taskSnapshots(ChatRequest request) {
        if (request == null || request.getTasks() == null) {
            return List.of();
        }
        return (List<TaskSnapshot>) request.getTasks();
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

    private boolean booleanInput(Object value, boolean defaultValue) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value).trim().toLowerCase();
        if (text.equals("true") || text.equals("yes")) {
            return true;
        }
        if (text.equals("false") || text.equals("no")) {
            return false;
        }
        return defaultValue;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
