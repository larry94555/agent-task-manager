# Agent Task Manager

A locally-run agent workspace built on [llama.cpp](https://github.com/ggerganov/llama.cpp) and Spring Boot. Run multiple parallel agent tasks in the browser, each with its own conversation context, and optionally drive them from a simple CSV script.

---

## Prerequisites

| Requirement | Version |
|---|---|
| Java (JDK) | 17 or higher |
| Node.js | 18 or higher |
| npm | bundled with Node.js |
| llama-server | from [llama.cpp](https://github.com/ggerganov/llama.cpp), available on `PATH` as `llama-server.exe` |

---

## Running Locally

### Step 1 — Start the backend

The backend is a Spring Boot application. On startup it automatically launches `llama-server` on port `8081` using the following configuration:

| Parameter | Value |
|---|---|
| Model | `unsloth/DeepSeek-R1-Distill-Llama-8B-GGUF:Q4_K_M` |
| Port | `8081` |
| Context size | `16384` |
| Threads | `8` |

From the repository root:

```bash
mvn spring-boot:run
```

Or build and run the JAR directly:

```bash
mvn package -DskipTests
java -jar target/demo-0.0.1-SNAPSHOT.jar
```

The backend listens on **http://localhost:8080**.

> **Changing the model:** Edit `src/main/java/com/example/simpleagent/demo/LlamaServerManager.java` and update the `-hf` argument in `startLlamaServer()`.

---

### Step 2 — Start the frontend

The frontend is a React + Vite application. The Vite dev server proxies all `/api` requests to the backend at `http://localhost:8080`, so no CORS configuration is needed.

```bash
cd frontend
npm install      # first time only
npm run dev
```

The frontend is served at **http://localhost:5173**.

---

### Step 3 — Open the app

Navigate to **http://localhost:5173** in your browser.

---

## CSV Scripting Language

The Script Runner lets you automate a sequence of agent interactions by loading a plain `.csv` file. Scripts execute asynchronously — you can freely interact with other tasks in the sidebar while a script is running.

### Loading a script

Click the **📂 Load Script** button at the bottom of the left sidebar. A standard file-picker dialog opens. If the file contains format errors they are displayed inline and the script does not run.

### File format

Each line is a numbered instruction:

```
<line_number>,<ACTION>,<PARAM>="<value>",<PARAM>="<value>"
```

- Lines starting with `#` are treated as comments and ignored.
- Blank lines are ignored.
- Parameter names are case-insensitive.
- `REF` is a script-local alias — it exists only within the script to connect `TASK` and `POST_AND_WAIT` steps together.

---

### Actions

#### `TASK` — create or reference a task

Creates a new agent task and binds it to a `REF` alias for use later in the script.

| Parameter | Required | Description |
|---|---|---|
| `NEW_NAME` | one of these two | Creates a new task with this display name. If a task with a matching name already exists (case-insensitive), a number suffix is appended automatically, e.g. `task 1 (2)`. |
| `NAME` | one of these two | References an existing task by its current display name (case-insensitive match). |
| `REF` | yes | A local alias used by subsequent `POST_AND_WAIT` steps to target this task. |

```csv
1,TASK,NEW_NAME="task 1",REF="agent"
```

---

#### `POST_AND_WAIT` — send a message and wait for the response

Posts a text message to the task identified by `REF`, then waits for the agent to respond before moving to the next line. The script always continues to the next step regardless of what the agent replies.

| Parameter | Required | Description |
|---|---|---|
| `TEXT` | yes | The message to send to the agent. |
| `REF` | yes | The alias of the target task, as defined by a preceding `TASK` step. |

```csv
2,POST_AND_WAIT,TEXT="What is your name",REF="agent"
```

---

### Example script

Save the following as a `.csv` file anywhere on your machine, then load it via the sidebar button.

```csv
# Example script — introduce the agent and confirm its name
1,TASK,NEW_NAME="task 1",REF="agent"
2,POST_AND_WAIT,TEXT="What is your name",REF="agent"
3,POST_AND_WAIT,TEXT="Your name is Joe.",REF="agent"
4,POST_AND_WAIT,TEXT="What is your name?",REF="agent"
```

**What this does:**

1. Creates a new task named `task 1` (or `task 1 (2)` if that name is already taken) and binds it to the ref `agent`.
2. Posts `"What is your name"` and waits for the agent to reply.
3. Posts `"Your name is Joe."` and waits for the agent to reply, regardless of what it said in step 2.
4. Posts `"What is your name?"` and waits for a final reply.

Sample scripts are provided in the [`samples/`](samples/) directory.

---

## Project Structure

```
agent-task-manager/
├── src/                    # Spring Boot backend
│   └── main/java/com/example/simpleagent/demo/
│       ├── ChatController.java       # POST /api/chat endpoint
│       ├── ChatRequest.java          # Request model
│       ├── ChatResponse.java         # Response model
│       ├── LlamaServerManager.java   # Manages the llama-server process
│       └── DemoApplication.java      # Spring Boot entry point
├── frontend/               # React + Vite frontend
│   └── src/
│       ├── App.jsx                   # Main app and task management
│       ├── useScriptRunner.js        # CSV script parser and executor
│       └── ScriptRunner.jsx          # Script Runner UI component
├── samples/                # Example CSV scripts
└── pom.xml
```

---

## API Reference

The backend exposes a single endpoint used by the frontend.

**`POST /api/chat`**

| Field | Type | Description |
|---|---|---|
| `taskId` | integer | Unique ID of the task |
| `taskName` | string | Display name of the task |
| `currentMessage` | string | The user's latest message |
| `conversationSummary` | string | Rolling summary of prior conversation turns |

Response:

| Field | Type | Description |
|---|---|---|
| `content` | string | The agent's reply |
| `updatedSummary` | string | The updated rolling conversation summary |
