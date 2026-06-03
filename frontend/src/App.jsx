import { useState, useRef, useCallback, useEffect } from 'react'
import './App.css'
import agentTaskImage from './assets/agent-task-orchestration.png'
import { useScriptRunner, makeUniqueName } from './useScriptRunner'
import { ScriptRunner } from './ScriptRunner'

const API_URL = '/api/chat'
const SESSION_FILE_VERSION = 1
const SESSION_FILE_APP = 'agent-task-manager'

function formatDuration(ms) {
  if (ms == null || Number.isNaN(ms)) return ''

  if (ms < 1000) {
    return `${Math.max(0, Math.round(ms))} ms`
  }

  if (ms < 60_000) {
    return `${(ms / 1000).toFixed(1)} s`
  }

  const minutes = Math.floor(ms / 60_000)
  const seconds = Math.round((ms % 60_000) / 1000)
  return `${minutes}m ${seconds}s`
}

function formatSessionTimestamp(date = new Date()) {
  const pad = (value) => String(value).padStart(2, '0')

  return [
    date.getFullYear(),
    pad(date.getMonth() + 1),
    pad(date.getDate()),
  ].join('-') + '-' + [
    pad(date.getHours()),
    pad(date.getMinutes()),
    pad(date.getSeconds()),
  ].join('')
}

function hasThinkingTasks(tasks) {
  return tasks.some((task) => task.status === 'thinking')
}

function normalizeTaskForSave(task) {
  return {
    ...task,
    requestStartedAt: null,
  }
}

function normalizeLoadedTask(task, fallbackId) {
  const id = Number.isFinite(Number(task?.id)) ? Number(task.id) : fallbackId
  const name = typeof task?.name === 'string' && task.name.trim()
    ? task.name.trim()
    : `Task ${id}`

  const allowedStatuses = new Set(['idle', 'done', 'stopped'])
  const status = allowedStatuses.has(task?.status) ? task.status : 'idle'

  const messages = Array.isArray(task?.messages)
    ? task.messages
        .filter((message) => message && typeof message.content === 'string')
        .map((message) => ({
          role: message.role === 'user' ? 'user' : 'assistant',
          content: message.content,
          ...(Number.isFinite(Number(message.durationMs))
            ? { durationMs: Number(message.durationMs) }
            : {}),
        }))
    : []

  return {
    id,
    name,
    messages,
    status,
    requestStartedAt: null,
    lastDurationMs: Number.isFinite(Number(task?.lastDurationMs))
      ? Number(task.lastDurationMs)
      : null,
  }
}

function validateSessionFile(session) {
  if (!session || typeof session !== 'object') {
    throw new Error('The selected file is not a valid session JSON object.')
  }

  if (session.app && session.app !== SESSION_FILE_APP) {
    throw new Error(`This file is for ${session.app}, not ${SESSION_FILE_APP}.`)
  }

  if (!session.state || typeof session.state !== 'object') {
    throw new Error('The session file is missing its state object.')
  }

  if (!Array.isArray(session.state.tasks)) {
    throw new Error('The session file is missing its tasks array.')
  }

  if (hasThinkingTasks(session.state.tasks)) {
    throw new Error('This session contains an in-progress request. Stop or finish active requests before saving a session.')
  }
}

function Task({ task, onClose, onEditName, onSendMessage, onStop, onRestart }) {
  const [inputText, setInputText] = useState('')
  const [editingName, setEditingName] = useState(false)
  const [nameDraft, setNameDraft] = useState(task.name)
  const [nowMs, setNowMs] = useState(Date.now())
  const messagesRef = useRef(null)
  const messagesEndRef = useRef(null)
  const shouldAutoScrollRef = useRef(true)

  useEffect(() => {
    if (task.status !== 'thinking') return

    setNowMs(Date.now())
    const intervalId = window.setInterval(() => {
      setNowMs(Date.now())
    }, 250)

    return () => window.clearInterval(intervalId)
  }, [task.status, task.requestStartedAt])

  const handleMessagesScroll = () => {
    const el = messagesRef.current
    if (!el) return

    const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight
    shouldAutoScrollRef.current = distanceFromBottom < 80
  }

  useEffect(() => {
    if (shouldAutoScrollRef.current) {
      messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
    }
  }, [task.messages.length, task.status])

  const handleSend = () => {
    if (!inputText.trim() || task.status === 'thinking') return

    onSendMessage(task.id, inputText.trim())
    setInputText('')
  }

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  const handleNameSubmit = () => {
    if (nameDraft.trim()) {
      onEditName(task.id, nameDraft.trim())
    }

    setEditingName(false)
  }

  const activeElapsedMs =
    task.status === 'thinking' && task.requestStartedAt
      ? nowMs - task.requestStartedAt
      : null

  return (
    <div className="task-panel">
      <div className="task-header">
        {editingName ? (
          <input
            className="task-name-input"
            value={nameDraft}
            onChange={(e) => setNameDraft(e.target.value)}
            onBlur={handleNameSubmit}
            onKeyDown={(e) => e.key === 'Enter' && handleNameSubmit()}
            autoFocus
          />
        ) : (
          <h3
            className="task-name"
            onClick={() => {
              setNameDraft(task.name)
              setEditingName(true)
            }}
            title="Click to edit name"
          >
            {task.name}
          </h3>
        )}

        <div className="task-actions">
          {task.status === 'thinking' && (
            <button className="btn btn-stop" onClick={() => onStop(task.id)} title="Stop thinking">
              â¹ Stop
            </button>
          )}

          {task.status === 'stopped' && (
            <button className="btn btn-restart" onClick={() => onRestart(task.id)} title="Restart task">
              â–¶ Restart
            </button>
          )}

          <button className="btn btn-close" onClick={() => onClose(task.id)} title="Return to task list">
            â† Back to Tasks
          </button>
        </div>
      </div>

      <div className="messages" ref={messagesRef} onScroll={handleMessagesScroll}>
        {task.messages.length === 0 && (
          <div className="empty-chat">
            No instructions yet. Give the agent a goal or ask a question.
          </div>
        )}

        {task.messages.map((msg, idx) => (
          <div key={idx} className={`message message-${msg.role}`}>
            <div className="message-label">
              {msg.role === 'user' ? 'Instruction' : 'Agent'}
            </div>
            <div className="message-content">{msg.content}</div>

            {msg.durationMs != null && (
              <div className="message-duration">
                Response time: {formatDuration(msg.durationMs)}
              </div>
            )}
          </div>
        ))}

        {task.status === 'thinking' && (
          <div className="message message-assistant thinking">
            <div className="message-label">Agent</div>
            <div className="message-content">
              <span className="thinking-dots">
                thinking
                <span className="dot-pulse"> ...</span>
              </span>
              {activeElapsedMs != null && (
                <span className="thinking-duration">
                  {formatDuration(activeElapsedMs)}
                </span>
              )}
            </div>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      <div className="input-area">
        <textarea
          className="message-input"
          placeholder="Give an instruction..."
          value={inputText}
          onChange={(e) => setInputText(e.target.value)}
          onKeyDown={handleKeyDown}
          disabled={task.status === 'thinking'}
          rows={2}
        />

        <button
          className="btn btn-send"
          onClick={handleSend}
          disabled={!inputText.trim() || task.status === 'thinking'}
        >
          Send
        </button>
      </div>
    </div>
  )
}

function App() {
  const [tasks, setTasks] = useState([])
  const [nextTaskId, setNextTaskId] = useState(1)
  const [selectedTaskId, setSelectedTaskId] = useState(null)
  const [scriptStatus, setScriptStatus] = useState('')
  const abortControllers = useRef({})
  const loadSessionInputRef = useRef(null)

  // Always-current mirror of tasks, safe to read regardless of
  // React's state-commit timing. This is used to build LLM context.
  const tasksRef = useRef(tasks)

  useEffect(() => {
    tasksRef.current = tasks
  }, [tasks])

  const createTask = () => {
    setTasks((prev) => {
      const desiredName = `Task ${nextTaskId}`
      const finalName = makeUniqueName(desiredName, prev.map((t) => t.name))
      const task = {
        id: nextTaskId,
        name: finalName,
        messages: [],
        status: 'idle',
        requestStartedAt: null,
        lastDurationMs: null,
      }

      setNextTaskId((n) => n + 1)
      setSelectedTaskId(task.id)

      return [...prev, task]
    })
  }

  const closeTask = useCallback((taskId) => {
    setSelectedTaskId((prev) => (prev === taskId ? null : prev))
  }, [])

  const editTaskName = useCallback((taskId, newName) => {
    setTasks((prev) => {
      const othersNames = prev
        .filter((t) => t.id !== taskId)
        .map((t) => t.name)

      const finalName = makeUniqueName(newName, othersNames)
      return prev.map((t) => (t.id === taskId ? { ...t, name: finalName } : t))
    })
  }, [])

  const saveSession = useCallback(() => {
    const currentTasks = tasksRef.current

    if (hasThinkingTasks(currentTasks)) {
      window.alert('Stop or finish active agent requests before saving the session.')
      return
    }

    const session = {
      app: SESSION_FILE_APP,
      version: SESSION_FILE_VERSION,
      savedAt: new Date().toISOString(),
      state: {
        tasks: currentTasks.map(normalizeTaskForSave),
        nextTaskId,
        selectedTaskId,
        scriptStatus,
      },
    }

    const blob = new Blob([JSON.stringify(session, null, 2)], {
      type: 'application/json',
    })

    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `dumb-barton-session-${formatSessionTimestamp()}.json`
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    URL.revokeObjectURL(url)
  }, [nextTaskId, scriptStatus, selectedTaskId])

  const openLoadSessionPicker = useCallback(() => {
    if (hasThinkingTasks(tasksRef.current)) {
      window.alert('Stop or finish active agent requests before loading another session.')
      return
    }

    loadSessionInputRef.current?.click()
  }, [])

  const handleLoadSessionFile = useCallback(async (event) => {
    const file = event.target.files?.[0]
    event.target.value = ''

    if (!file) return

    try {
      const text = await file.text()
      const session = JSON.parse(text)
      validateSessionFile(session)

      const loadedTasks = session.state.tasks.map((task, index) =>
        normalizeLoadedTask(task, index + 1)
      )

      const maxTaskId = loadedTasks.reduce((max, task) => Math.max(max, task.id), 0)
      const loadedNextTaskId = Number.isFinite(Number(session.state.nextTaskId))
        ? Math.max(Number(session.state.nextTaskId), maxTaskId + 1)
        : maxTaskId + 1

      const loadedSelectedTaskId = loadedTasks.some(
        (task) => task.id === session.state.selectedTaskId
      )
        ? session.state.selectedTaskId
        : null

      const loadedScriptStatus = typeof session.state.scriptStatus === 'string'
        ? session.state.scriptStatus
        : ''

      tasksRef.current = loadedTasks
      setTasks(loadedTasks)
      setNextTaskId(loadedNextTaskId)
      setSelectedTaskId(loadedSelectedTaskId)
      setScriptStatus(loadedScriptStatus)
    } catch (err) {
      window.alert(`Could not load session: ${err.message}`)
    }
  }, [])

  const sendMessage = useCallback(async (taskId, text) => {
    const startedAt = performance.now()
    const userMessage = { role: 'user', content: text }

    // Read prior messages from the always-current ref.
    const currentTask = tasksRef.current.find((t) => t.id === taskId)
    const priorMessages = currentTask ? currentTask.messages : []

    // IMPORTANT:
    // Frontend-only metadata such as durationMs is intentionally NOT included here.
    // Only role + content are sent to the backend/LLM.
    const context = priorMessages.map((m) =>
      m.role === 'user' ? `user: ${m.content}` : `agent: ${m.content}`
    )

    const afterUser = tasksRef.current.map((t) =>
      t.id === taskId
        ? {
            ...t,
            messages: [...t.messages, userMessage],
            status: 'thinking',
            requestStartedAt: Date.now(),
            lastDurationMs: null,
          }
        : t
    )

    tasksRef.current = afterUser
    setTasks(afterUser)

    const controller = new AbortController()
    abortControllers.current[taskId] = controller

    const requestBody = {
      taskId,
      context,
      latest: `user: ${text}`,
    }

    try {
      const response = await fetch(API_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(requestBody),
        signal: controller.signal,
      })

      const durationMs = performance.now() - startedAt

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`)
      }

      const result = await response.json()
      const aiMessage = {
        role: 'assistant',
        content: result.content,
        durationMs,
      }

      const afterAgent = tasksRef.current.map((t) =>
        t.id === taskId
          ? {
              ...t,
              messages: [...t.messages, aiMessage],
              status: 'done',
              requestStartedAt: null,
              lastDurationMs: durationMs,
            }
          : t
      )

      tasksRef.current = afterAgent
      setTasks(afterAgent)

      return result.updatedSummary
    } catch (err) {
      const durationMs = performance.now() - startedAt

      if (err.name === 'AbortError') {
        const afterAbort = tasksRef.current.map((t) =>
          t.id === taskId
            ? {
                ...t,
                status: 'stopped',
                requestStartedAt: null,
                lastDurationMs: durationMs,
              }
            : t
        )

        tasksRef.current = afterAbort
        setTasks(afterAbort)
        return undefined
      }

      const errorMessage = {
        role: 'assistant',
        content: `Error: ${err.message}`,
        durationMs,
      }

      const afterError = tasksRef.current.map((t) =>
        t.id === taskId
          ? {
              ...t,
              messages: [...t.messages, errorMessage],
              status: 'done',
              requestStartedAt: null,
              lastDurationMs: durationMs,
            }
          : t
      )

      tasksRef.current = afterError
      setTasks(afterError)

      return undefined
    } finally {
      delete abortControllers.current[taskId]
    }
  }, [])

  const stopThinking = useCallback((taskId) => {
    abortControllers.current[taskId]?.abort()
  }, [])

  const restartTask = useCallback((taskId) => {
    setTasks((prev) => {
      const next = prev.map((t) =>
        t.id === taskId
          ? {
              ...t,
              status: 'idle',
              requestStartedAt: null,
            }
          : t
      )

      tasksRef.current = next
      return next
    })
  }, [])

  const selectedTask = tasks.find((t) => t.id === selectedTaskId)
  const sessionActionDisabled = hasThinkingTasks(tasks)

  const { runScript } = useScriptRunner({
    tasks,
    setTasks,
    nextTaskId,
    setNextTaskId,
    sendMessage,
    setScriptStatus,
  })

  return (
    <div className="app">
      <header className="app-header">
        <div className="brand">
          <div className="brand-mark">DB</div>
          <div className="brand-copy">
            <h1>Dumb Barton</h1>
            <p>The simple agent that runs locally on your machine.</p>
          </div>
        </div>

        <div className="header-actions">
          <button className="btn btn-secondary" onClick={saveSession} disabled={sessionActionDisabled}>
            Save Session
          </button>

          <button className="btn btn-secondary" onClick={openLoadSessionPicker} disabled={sessionActionDisabled}>
            Load Session
          </button>

          <input
            ref={loadSessionInputRef}
            className="session-file-input"
            type="file"
            accept="application/json,.json"
            onChange={handleLoadSessionFile}
          />

          <button className="btn btn-primary" onClick={createTask}>
            + New Agent Task
          </button>
        </div>
      </header>

      <div className="main-layout">
        <aside className="task-list">
          <h2>Agent Tasks</h2>

          {tasks.length === 0 ? (
            <div className="no-tasks">
              No agent tasks yet.
              <br />
              Create one and give Dumb Barton a goal.
            </div>
          ) : (
            <ul>
              {tasks.map((task) => (
                <li
                  key={task.id}
                  className={`task-list-item ${
                    task.id === selectedTaskId ? 'active' : ''
                  }`}
                  onClick={() => setSelectedTaskId(task.id)}
                >
                  <span className="task-list-name">{task.name}</span>
                  <span className={`task-status status-${task.status}`}>
                    {task.status === 'thinking'
                      ? 'â—'
                      : task.status === 'stopped'
                        ? 'â¸'
                        : ''}
                  </span>
                </li>
              ))}
            </ul>
          )}

          <ScriptRunner onRun={runScript} scriptStatus={scriptStatus} />
        </aside>

        <main className="chat-area">
          {selectedTask ? (
            <Task
              key={selectedTask.id}
              task={selectedTask}
              onClose={closeTask}
              onEditName={editTaskName}
              onSendMessage={sendMessage}
              onStop={stopThinking}
              onRestart={restartTask}
            />
          ) : tasks.length > 0 ? (
            <div className="task-picker">
              <h2>Select an agent task</h2>
              <p>Choose an existing task or create a new one.</p>

              <div className="task-picker-grid">
                {tasks.map((task) => (
                  <button
                    key={task.id}
                    className="task-picker-card"
                    onClick={() => setSelectedTaskId(task.id)}
                  >
                    <span className="task-picker-name">{task.name}</span>
                    <span className={`task-status status-${task.status}`}>
                      {task.status === 'thinking'
                        ? 'â— Thinking'
                        : task.status === 'stopped'
                          ? 'â¸ Stopped'
                          : 'Open'}
                    </span>
                  </button>
                ))}
              </div>
            </div>
          ) : (
            <div className="no-task-selected">
              <img
                className="no-task-hero"
                src={agentTaskImage}
                alt="AI agent hub connected to goals, questions, and task cards"
              />
              <div className="empty-state-badge">Local agent workspace</div>
              <h2>Give Dumb Barton a goal.</h2>
              <p>
                Create an agent task, then ask a question, define an objective,
                or give a concrete instruction. This is not just a chat window â€”
                it is a workspace for directing a local agent running on your machine.
              </p>
              <button className="btn btn-primary empty-state-button" onClick={createTask}>
                Create Agent Task
              </button>
            </div>
          )}
        </main>
      </div>
    </div>
  )
}

export default App

