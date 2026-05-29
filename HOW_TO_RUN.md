# SimpleAgent - How to Run

## Project Structure
```
~/github/demo/              -- Spring Boot backend (REST API)
~/github/demo/frontend/     -- React frontend (UI)
```

## Prerequisites
- **Java 17+** installed (JDK)
- **Node.js 18+** and **npm** installed
- **llama-server** from llama.cpp installed and available on PATH as `llama-server.exe`

## Step 1: Start the Backend

The backend is a Spring Boot application that:
- Starts `llama-server` automatically on port 8081
- Exposes a REST endpoint at `POST /api/chat` that accepts `{ "text": "your prompt" }`
- Forwards the prompt to llama-server and returns the response

### To start the backend:
```bash
cd ~/github/demo
mvn spring-boot:run
```

Or build and run the JAR:
```bash
cd ~/github/demo
mvn package -DskipTests
java -jar target/demo-0.0.1-SNAPSHOT.jar
```

The backend will start on **http://localhost:8080**.

When the backend starts, it will automatically launch `llama-server.exe` with:
- Model: `unsloth/DeepSeek-R1-Distill-Llama-8B-GGUF:Q4_K_M`
- Port: `8081`
- Context size: `16384`
- Threads: `8`

> **Note:** If you need to use a different model, edit `src/main/java/com/example/simpleagent/demo/LlamaServerManager.java` and change the `-hf` parameter.

## Step 2: Start the Frontend

The frontend is a React application built with Vite.

### To start the frontend:
```bash
cd ~/github/demo/frontend
npm run dev
```

The frontend will start on **http://localhost:5173**.

The Vite dev server proxies `/api` requests to `http://localhost:8080`, so the frontend can communicate with the backend without CORS issues.

## Step 3: Open the Application

Open a browser and navigate to: **http://localhost:5173**

## How to Test the Application

### Creating a Task
1. Click the **"+ New Task"** button in the top-right corner
2. Enter a task name (e.g., "My First Chat")
3. Click **"Create"**
4. The task will appear in the sidebar and open in the main chat area

### Opening an Existing Task
- Click on any task name in the left sidebar to open it

### Editing a Task Name
- Click on the task name in the chat header to edit it inline

### Sending a Message
1. Type your message in the text input at the bottom
2. Press **Enter** or click **"Send"**
3. The message appears in the chat and the AI shows "thinking..."
4. After 30 seconds (or when the backend responds), the AI response appears

### Stopping a Response
- While the AI is "thinking", click the **"Stop"** button in the task header
- The task status changes to "stopped"
- You can now type a new message and send it

### Restarting a Stopped Task
- If a task is stopped, click the **"Restart"** button to reset its status
- You can then send new messages

### Multiple Tasks
- You can create multiple tasks and switch between them
- Each task maintains its own message history
- You can send messages to one task even while another is "thinking"

### Closing a Task
- Click the **"Close"** button in the task header
- The task is removed from the sidebar

## API Endpoint

The backend exposes one endpoint:

**POST /api/chat**
- Request body: `{ "text": "Your prompt here" }`
- Response: The AI-generated text response (plain text)

Example:
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"text": "Hello, how are you?"}'