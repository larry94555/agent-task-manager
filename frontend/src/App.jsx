import { useState, useRef, useCallback, useEffect } from 'react'
import './App.css'
import agentTaskImage from './assets/agent-task-orchestration.png'
import { useScriptRunner, makeUniqueName } from './useScriptRunner'
import { ScriptRunner } from './ScriptRunner'

const API_URL = '/api/chat'
const SESSION_FILE_VERSION = 2
const SESSION_FILE_APP = 'agent-task-manager'

function formatDuration(ms) {
  if (ms == null || Number.isNaN(ms)) return ''

  if (ms < 1000) return `${Math.max(0, Math.round(ms))} ms`
  if (ms < 60_000) return `${(ms / 1000).toFixed(1)} s`

  const minutes = Math.floor(ms / 60_000)
  const seconds = Math.round((ms % 60_000) / 1000)
  return `${minutes}m ${seconds}s`
}

function formatSessionTimestamp(date = new Date()) {
  const pad = (value) => String(value).padStart(2, '0')

  return [date.getFullYear(), pad(date.getMonth() + 1), pad(date.getDate())].join('-')
    + '-'
    + [pad(date.getHours()), pad(date.getMinutes()), pad(date.getSeconds())].join('')
}

function safeFilePart(value) {
  const cleaned = String(value || 'task')
    .trim()
    .replace(/[<>:"/\\|?*]+/g, '-')
    .replace(/\s+/g, '-')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '')

  return cleaned || 'task'
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

function normalizeMessage(message) {
  const role = ['user', 'assistant', 'tool'].includes(message?.role) ? message.role : 'assistant'

  return {
    role,
    content: typeof message?.content === 'string' ? message.content : '',
    ...(message?.type ? { type: message.type } : {}),
    ...(message?.action ? { action: message.action } : {}),
    ...(Number.isFinite(Number(message?.taskId)) ? { taskId: Number(message.taskId) } : {}),
    ...(Number.isFinite(Number(message?.parentTaskId)) ? { parentTaskId: Number(message.parentTaskId) } : {}),
    ...(typeof message?.taskName === 'string' ? { taskName: message.taskName } : {}),
    ...(Number.isFinite(Number(message?.durationMs)) ? { durationMs: Number(message.durationMs) } : {}),
  }
}

function normalizeLoadedTask(task, fallbackId) {
  const id = Number.isFinite(Number(task?.id)) ? Number(task.id) : fallbackId
  const name = typeof task?.name === 'string' && task.name.trim() ? task.name.trim() : `Task ${id}`
  const allowedStatuses = new Set(['idle', 'thinking', 'done', 'stopped'])
  const status = allowedStatuses.has(task?.status) && task.status !== 'thinking' ? task.status : 'idle'
  const lifecycle = task?.lifecycle === 'closed' ? 'closed' : 'open'
  const createdBy = ['user', 'agent', 'script'].includes(task?.createdBy) ? task.createdBy : 'user'
  const parentTaskId = Number.isFinite(Number(task?.parentTaskId)) ? Number(task.parentTaskId) : null

  const messages = Array.isArray(task?.messages)
    ? task.messages.filter((message) => message && typeof message.content === 'string').map(normalizeMessage)
    : []

  return {
    id,
    name,
    messages,
    status,
    lifecycle,
    createdBy,
    parentTaskId,
    createdAt: typeof task?.createdAt === 'string' ? task.createdAt : new Date().toISOString(),
    requestStartedAt: null,
    lastDurationMs: Number.isFinite(Number(task?.lastDurationMs)) ? Number(task.lastDurationMs) : null,
  }
}

function validateSessionFile(session) {
  if (!session || typeof session !== 'object') throw new Error('The selected file is not a valid session JSON object.')
  if (session.app && session.app !== SESSION_FILE_APP) throw new Error(`This file is for ${session.app}, not ${SESSION_FILE_APP}.`)
  if (!session.state || typeof session.state !== 'object') throw new Error('The session file is missing its state object.')
  if (!Array.isArray(session.state.tasks)) throw new Error('The session file is missing its tasks array.')
  if (hasThinkingTasks(session.state.tasks)) throw new Error('This session contains an in-progress request. Stop or finish active requests before saving a session.')
}

function escapeScriptValue(value) {
  return String(value ?? '')
    .replace(/\\/g, '\\\\')
    .replace(/\r/g, '\\r')
    .replace(/\n/g, '\\n')
    .replace(/\t/g, '\\t')
    .replace(/"/g, '\\"')
}

function buildTaskReplayScript(task) {
  const userMessages = task.messages.filter(
    (message) => message.role === 'user' && typeof message.content === 'string'
  )

  if (userMessages.length === 0) {
    throw new Error('The selected task does not have any user instructions to export.')
  }

  const ref = 'task1'
  const lines = [
    '# Dumb Barton replay script',
    `# Exported: ${new Date().toISOString()}`,
    `# Source task: ${task.name}`,
    '#',
    '# This script replays the user instructions through the existing Script Runner.',
    '# It does not preserve old agent responses verbatim; loading it will generate new responses.',
    '',
    `1,TASK,NEW_NAME="${escapeScriptValue(task.name)}",REF="${ref}"`,
  ]

  userMessages.forEach((message, index) => {
    lines.push(`${index + 2},POST_AND_WAIT,REF="${ref}",TEXT="${escapeScriptValue(message.content)}"`)
  })

  return lines.join('\n') + '\n'
}

async function saveTextFileWithDialog({ text, suggestedName, mimeType, description, extensions }) {
  if (typeof window.showSaveFilePicker === 'function') {
    try {
      const fileHandle = await window.showSaveFilePicker({
        suggestedName,
        types: [{ description, accept: { [mimeType]: extensions } }],
      })

      const writable = await fileHandle.createWritable()
      await writable.write(text)
      await writable.close()
      return true
    } catch (err) {
      if (err?.name === 'AbortError') return false
      console.warn('Save picker failed. Falling back to browser download.', err)
    }
  }

  const blob = new Blob([text], { type: mimeType })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = suggestedName
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
  return true
}

function makeTaskSnapshot(tasks) {
  return tasks.map((task) => ({
    id: task.id,
    name: task.name,
    status: task.status,
    lifecycle: task.lifecycle ?? 'open',
    parentTaskId: task.parentTaskId ?? null,
    createdBy: task.createdBy ?? 'user',
  }))
}

function taskContextLine(message) {
  if (message.role === 'user') return `user: ${message.content}`
  if (message.role === 'assistant') return `agent: ${message.content}`
  if (message.role === 'tool') return `task action: ${message.content}`
  return null
}

function createToolMessage({ action, taskId, parentTaskId, taskName, content }) {
  return {
    role: 'tool',
    type: 'task_action',
    action,
    taskId,
    parentTaskId,
    taskName,
    content,
  }
}

function applyClientTaskActions({ currentTasks, currentNextTaskId, currentSelectedTaskId, sourceTaskId, taskActions }) {
  if (!Array.isArray(taskActions) || taskActions.length === 0) {
    return { tasks: currentTasks, nextTaskId: currentNextTaskId, selectedTaskId: currentSelectedTaskId }
  }

  let tasks = [...currentTasks]
  let nextTaskId = currentNextTaskId
  let selectedTaskId = currentSelectedTaskId
  const toolMessages = []

  for (const action of taskActions) {
    if (!action || typeof action.type !== 'string') continue

    if (action.type === 'create_task') {
      const name = makeUniqueName(action.name || `Task ${nextTaskId}`, tasks.map((task) => task.name))
      const newTask = {
        id: nextTaskId,
        name,
        messages: [],
        status: 'idle',
        lifecycle: 'open',
        createdBy: 'agent',
        parentTaskId: Number.isFinite(Number(action.parentTaskId)) ? Number(action.parentTaskId) : sourceTaskId,
        createdAt: new Date().toISOString(),
        requestStartedAt: null,
        lastDurationMs: null,
      }

      tasks = [...tasks, newTask]
      toolMessages.push(createToolMessage({
        action: 'create_task',
        taskId: newTask.id,
        parentTaskId: newTask.parentTaskId,
        taskName: newTask.name,
        content: `Created child task: ${newTask.name}`,
      }))

      if (action.switchToTask === true) selectedTaskId = newTask.id
      nextTaskId += 1
      continue
    }

    if (action.type === 'rename_task') {
      const targetTaskId = Number(action.taskId)
      if (!Number.isFinite(targetTaskId)) continue

      const oldTask = tasks.find((task) => task.id === targetTaskId)
      const existingNames = tasks.filter((task) => task.id !== targetTaskId).map((task) => task.name)
      const finalName = makeUniqueName(action.name || `Task ${targetTaskId}`, existingNames)

      tasks = tasks.map((task) => (task.id === targetTaskId ? { ...task, name: finalName } : task))
      toolMessages.push(createToolMessage({
        action: 'rename_task',
        taskId: targetTaskId,
        taskName: finalName,
        content: `Renamed task${oldTask ? ` from "${oldTask.name}"` : ''} to "${finalName}".`,
      }))
      continue
    }

    if (action.type === 'close_task') {
      const targetTaskId = Number(action.taskId)
      if (!Number.isFinite(targetTaskId)) continue

      const targetTask = tasks.find((task) => task.id === targetTaskId)
      tasks = tasks.map((task) => (task.id === targetTaskId ? { ...task, lifecycle: 'closed' } : task))
      toolMessages.push(createToolMessage({
        action: 'close_task',
        taskId: targetTaskId,
        taskName: targetTask?.name,
        content: `Closed task: ${targetTask?.name ?? targetTaskId}.`,
      }))
    }
  }

  if (toolMessages.length > 0) {
    tasks = tasks.map((task) => (task.id === sourceTaskId ? { ...task, messages: [...task.messages, ...toolMessages] } : task))
  }

  return { tasks, nextTaskId, selectedTaskId }
}

function getTaskDepth(task, taskById) {
  let depth = 0
  let parentId = task.parentTaskId
  const seen = new Set()

  while (parentId != null && taskById.has(parentId) && !seen.has(parentId) && depth < 4) {
    seen.add(parentId)
    depth += 1
    parentId = taskById.get(parentId)?.parentTaskId
  }

  return depth
}

function orderTasksForDisplay(tasks) {
  const byParent = new Map()
  const byId = new Map(tasks.map((task) => [task.id, task]))
  const ordered = []

  for (const task of tasks) {
    const parentId = task.parentTaskId ?? null
    if (!byParent.has(parentId)) byParent.set(parentId, [])
    byParent.get(parentId).push(task)
  }

  const visit = (task) => {
    ordered.push(task)
    for (const child of byParent.get(task.id) ?? []) visit(child)
  }

  for (const root of byParent.get(null) ?? []) visit(root)
  for (const task of tasks) {
    if (!ordered.some((candidate) => candidate.id === task.id)) ordered.push(task)
  }

  return ordered.map((task) => ({ task, depth: getTaskDepth(task, byId) }))
}

function Task({ task, onBack, onEditName, onSendMessage, onStop, onRestart, onReopen, onOpenTask }) {
  const [inputText, setInputText] = useState('')
  const [editingName, setEditingName] = useState(false)
  const [nameDraft, setNameDraft] = useState(task.name)
  const [nowMs, setNowMs] = useState(Date.now())
  const messagesRef = useRef(null)
  const messagesEndRef = useRef(null)
  const shouldAutoScrollRef = useRef(true)

  const isClosed = task.lifecycle === 'closed'

  useEffect(() => {
    if (task.status !== 'thinking') return undefined

    setNowMs(Date.now())
    const intervalId = window.setInterval(() => setNowMs(Date.now()), 250)
    return () => window.clearInterval(intervalId)
  }, [task.status, task.requestStartedAt])

  const handleMessagesScroll = () => {
    const el = messagesRef.current
    if (!el) return
    const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight
    shouldAutoScrollRef.current = distanceFromBottom < 80
  }

  useEffect(() => {
    if (shouldAutoScrollRef.current) messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [task.messages.length, task.status])

  const handleSend = () => {
    if (!inputText.trim() || task.status === 'thinking' || isClosed) return
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
    if (nameDraft.trim()) onEditName(task.id, nameDraft.trim())
    setEditingName(false)
  }

  const activeElapsedMs = task.status === 'thinking' && task.requestStartedAt ? nowMs - task.requestStartedAt : null

  return (
    <div className="task-panel">
      <div className="task-header">
        <div className="task-title-area">
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

          <div className="task-meta-line">
            {task.parentTaskId != null && <span>Child of task {task.parentTaskId}</span>}
            {task.createdBy && <span>Created by {task.createdBy}</span>}
            {isClosed && <span className="task-closed-badge">Closed</span>}
          </div>
        </div>

        <div className="task-actions">
          {task.status === 'thinking' && (
            <button className="btn btn-stop" onClick={() => onStop(task.id)} title="Stop thinking">
              Stop
            </button>
          )}

          {task.status === 'stopped' && (
            <button className="btn btn-restart" onClick={() => onRestart(task.id)} title="Restart task">
              Restart
            </button>
          )}

          {isClosed && (
            <button className="btn btn-secondary" onClick={() => onReopen(task.id)} title="Reopen task">
              Reopen
            </button>
          )}

          <button className="btn btn-close" onClick={() => onBack(task.id)} title="Return to task list">
            Back to Tasks
          </button>
        </div>
      </div>

      <div className="messages" ref={messagesRef} onScroll={handleMessagesScroll}>
        {task.messages.length === 0 && (
          <div className="empty-chat">No instructions yet. Give the agent a goal or ask a question.</div>
        )}

        {task.messages.map((msg, idx) => {
          if (msg.role === 'tool') {
            return (
              <div key={idx} className="message message-tool">
                <div className="message-label">Task Action</div>
                <div className="message-content">{msg.content}</div>
                {msg.taskId != null && (
                  <button className="btn btn-inline" onClick={() => onOpenTask(msg.taskId)}>
                    Open Task
                  </button>
                )}
              </div>
            )
          }

          return (
            <div key={idx} className={`message message-${msg.role}`}>
              <div className="message-label">{msg.role === 'user' ? 'Instruction' : 'Agent'}</div>
              <div className="message-content">{msg.content}</div>
              {msg.durationMs != null && (
                <div className="message-duration">Response time: {formatDuration(msg.durationMs)}</div>
              )}
            </div>
          )
        })}

        {task.status === 'thinking' && (
          <div className="message message-assistant thinking">
            <div className="message-label">Agent</div>
            <div className="message-content">
              <span className="thinking-dots">thinking<span className="dot-pulse"> ...</span></span>
              {activeElapsedMs != null && <span className="thinking-duration">{formatDuration(activeElapsedMs)}</span>}
            </div>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      {isClosed && (
        <div className="closed-task-notice">This task is closed. Reopen it before sending another instruction.</div>
      )}

      <div className="input-area">
        <textarea
          className="message-input"
          placeholder={isClosed ? 'Reopen this task to send another instruction...' : 'Give an instruction...'}
          value={inputText}
          onChange={(e) => setInputText(e.target.value)}
          onKeyDown={handleKeyDown}
          disabled={task.status === 'thinking' || isClosed}
          rows={2}
        />

        <button className="btn btn-send" onClick={handleSend} disabled={!inputText.trim() || task.status === 'thinking' || isClosed}>
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
  const tasksRef = useRef(tasks)
  const nextTaskIdRef = useRef(nextTaskId)
  const selectedTaskIdRef = useRef(selectedTaskId)

  useEffect(() => { tasksRef.current = tasks }, [tasks])
  useEffect(() => { nextTaskIdRef.current = nextTaskId }, [nextTaskId])
  useEffect(() => { selectedTaskIdRef.current = selectedTaskId }, [selectedTaskId])

  const createTask = () => {
    setTasks((prev) => {
      const desiredName = `Task ${nextTaskIdRef.current}`
      const finalName = makeUniqueName(desiredName, prev.map((t) => t.name))
      const task = {
        id: nextTaskIdRef.current,
        name: finalName,
        messages: [],
        status: 'idle',
        lifecycle: 'open',
        createdBy: 'user',
        parentTaskId: null,
        createdAt: new Date().toISOString(),
        requestStartedAt: null,
        lastDurationMs: null,
      }

      const next = [...prev, task]
      tasksRef.current = next
      nextTaskIdRef.current += 1
      selectedTaskIdRef.current = task.id
      setNextTaskId(nextTaskIdRef.current)
      setSelectedTaskId(task.id)
      return next
    })
  }

  const backToTasks = useCallback((taskId) => {
    setSelectedTaskId((prev) => (prev === taskId ? null : prev))
  }, [])

  const reopenTask = useCallback((taskId) => {
    setTasks((prev) => {
      const next = prev.map((task) => task.id === taskId ? { ...task, lifecycle: 'open' } : task)
      tasksRef.current = next
      return next
    })
  }, [])

  const editTaskName = useCallback((taskId, newName) => {
    setTasks((prev) => {
      const othersNames = prev.filter((t) => t.id !== taskId).map((t) => t.name)
      const finalName = makeUniqueName(newName, othersNames)
      const next = prev.map((t) => (t.id === taskId ? { ...t, name: finalName } : t))
      tasksRef.current = next
      return next
    })
  }, [])

  const saveSession = useCallback(async () => {
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
        nextTaskId: nextTaskIdRef.current,
        selectedTaskId: selectedTaskIdRef.current,
        scriptStatus,
      },
    }

    await saveTextFileWithDialog({
      text: JSON.stringify(session, null, 2),
      suggestedName: `dumb-barton-session-${formatSessionTimestamp()}.json`,
      mimeType: 'application/json',
      description: 'Dumb Barton Session JSON',
      extensions: ['.json'],
    })
  }, [scriptStatus])

  const saveSelectedTaskAsScript = useCallback(async () => {
    const currentTasks = tasksRef.current
    if (hasThinkingTasks(currentTasks)) {
      window.alert('Stop or finish active agent requests before saving a task script.')
      return
    }

    const selectedTask = currentTasks.find((task) => task.id === selectedTaskIdRef.current)
    if (!selectedTask) {
      window.alert('Select a task before saving a task script.')
      return
    }

    try {
      await saveTextFileWithDialog({
        text: buildTaskReplayScript(selectedTask),
        suggestedName: `dumb-barton-task-${safeFilePart(selectedTask.name)}-${formatSessionTimestamp()}.csv`,
        mimeType: 'text/csv',
        description: 'Dumb Barton Script CSV',
        extensions: ['.csv', '.txt'],
      })
    } catch (err) {
      window.alert(`Could not save task script: ${err.message}`)
    }
  }, [])

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
      const session = JSON.parse(await file.text())
      validateSessionFile(session)

      const loadedTasks = session.state.tasks.map((task, index) => normalizeLoadedTask(task, index + 1))
      const maxTaskId = loadedTasks.reduce((max, task) => Math.max(max, task.id), 0)
      const loadedNextTaskId = Number.isFinite(Number(session.state.nextTaskId))
        ? Math.max(Number(session.state.nextTaskId), maxTaskId + 1)
        : maxTaskId + 1
      const loadedSelectedTaskId = loadedTasks.some((task) => task.id === session.state.selectedTaskId)
        ? session.state.selectedTaskId
        : null
      const loadedScriptStatus = typeof session.state.scriptStatus === 'string' ? session.state.scriptStatus : ''

      tasksRef.current = loadedTasks
      nextTaskIdRef.current = loadedNextTaskId
      selectedTaskIdRef.current = loadedSelectedTaskId
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
    const currentTask = tasksRef.current.find((t) => t.id === taskId)
    const priorMessages = currentTask ? currentTask.messages : []

    const context = priorMessages
      .map(taskContextLine)
      .filter(Boolean)

    const afterUser = tasksRef.current.map((t) =>
      t.id === taskId
        ? { ...t, messages: [...t.messages, userMessage], status: 'thinking', requestStartedAt: Date.now(), lastDurationMs: null }
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
      tasks: makeTaskSnapshot(afterUser),
    }

    try {
      const response = await fetch(API_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(requestBody),
        signal: controller.signal,
      })

      const durationMs = performance.now() - startedAt
      if (!response.ok) throw new Error(`HTTP ${response.status}`)

      const result = await response.json()
      const aiMessage = { role: 'assistant', content: result.content, durationMs }
      const afterAgentBase = tasksRef.current.map((t) =>
        t.id === taskId
          ? { ...t, messages: [...t.messages, aiMessage], status: 'done', requestStartedAt: null, lastDurationMs: durationMs }
          : t
      )

      const applied = applyClientTaskActions({
        currentTasks: afterAgentBase,
        currentNextTaskId: nextTaskIdRef.current,
        currentSelectedTaskId: selectedTaskIdRef.current,
        sourceTaskId: taskId,
        taskActions: result.taskActions,
      })

      tasksRef.current = applied.tasks
      nextTaskIdRef.current = applied.nextTaskId
      selectedTaskIdRef.current = applied.selectedTaskId
      setTasks(applied.tasks)
      setNextTaskId(applied.nextTaskId)
      setSelectedTaskId(applied.selectedTaskId)

      return result.updatedSummary
    } catch (err) {
      const durationMs = performance.now() - startedAt

      if (err.name === 'AbortError') {
        const afterAbort = tasksRef.current.map((t) =>
          t.id === taskId ? { ...t, status: 'stopped', requestStartedAt: null, lastDurationMs: durationMs } : t
        )
        tasksRef.current = afterAbort
        setTasks(afterAbort)
        return undefined
      }

      const errorMessage = { role: 'assistant', content: `Error: ${err.message}`, durationMs }
      const afterError = tasksRef.current.map((t) =>
        t.id === taskId
          ? { ...t, messages: [...t.messages, errorMessage], status: 'done', requestStartedAt: null, lastDurationMs: durationMs }
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
      const next = prev.map((t) => t.id === taskId ? { ...t, status: 'idle', requestStartedAt: null } : t)
      tasksRef.current = next
      return next
    })
  }, [])

  const selectedTask = tasks.find((t) => t.id === selectedTaskId)
  const sessionActionDisabled = hasThinkingTasks(tasks)
  const saveTaskScriptDisabled = sessionActionDisabled || !selectedTask
  const displayedTasks = orderTasksForDisplay(tasks)
  const openDisplayedTasks = displayedTasks.filter(({ task }) => task.lifecycle !== 'closed')
  const closedDisplayedTasks = displayedTasks.filter(({ task }) => task.lifecycle === 'closed')

  const { runScript } = useScriptRunner({
    setTasks,
    nextTaskId,
    setNextTaskId,
    sendMessage,
    setScriptStatus,
  })

  const renderTaskListItem = ({ task, depth }) => (
    <li
      key={task.id}
      className={`task-list-item ${task.id === selectedTaskId ? 'active' : ''} ${task.lifecycle === 'closed' ? 'closed' : ''}`}
      onClick={() => setSelectedTaskId(task.id)}
      style={{ paddingLeft: `${12 + depth * 18}px` }}
    >
      <span className="task-list-name">
        {depth > 0 && <span className="task-child-marker">â†³</span>}
        {task.name}
      </span>
      <span className={`task-status status-${task.status}`}>
        {task.status === 'thinking' ? 'â—' : task.status === 'stopped' ? 'â¸' : task.lifecycle === 'closed' ? 'Closed' : ''}
      </span>
    </li>
  )

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
          <button className="btn btn-secondary" onClick={saveSession} disabled={sessionActionDisabled}>Save Session</button>
          <button className="btn btn-secondary" onClick={openLoadSessionPicker} disabled={sessionActionDisabled}>Load Session</button>
          <button className="btn btn-secondary" onClick={saveSelectedTaskAsScript} disabled={saveTaskScriptDisabled}>Save Task Script</button>
          <input ref={loadSessionInputRef} className="session-file-input" type="file" accept="application/json,.json" onChange={handleLoadSessionFile} />
          <button className="btn btn-primary" onClick={createTask}>+ New Agent Task</button>
        </div>
      </header>

      <div className="main-layout">
        <aside className="task-list">
          <h2>Agent Tasks</h2>

          {tasks.length === 0 ? (
            <div className="no-tasks">No agent tasks yet.<br />Create one and give Dumb Barton a goal.</div>
          ) : (
            <>
              <div className="task-section-label">Open Tasks</div>
              <ul>{openDisplayedTasks.map(renderTaskListItem)}</ul>

              {closedDisplayedTasks.length > 0 && (
                <>
                  <div className="task-section-label closed-label">Closed Tasks</div>
                  <ul>{closedDisplayedTasks.map(renderTaskListItem)}</ul>
                </>
              )}
            </>
          )}

          <ScriptRunner onRun={runScript} scriptStatus={scriptStatus} />
        </aside>

        <main className="chat-area">
          {selectedTask ? (
            <Task
              key={selectedTask.id}
              task={selectedTask}
              onBack={backToTasks}
              onEditName={editTaskName}
              onSendMessage={sendMessage}
              onStop={stopThinking}
              onRestart={restartTask}
              onReopen={reopenTask}
              onOpenTask={setSelectedTaskId}
            />
          ) : tasks.length > 0 ? (
            <div className="task-picker">
              <h2>Select an agent task</h2>
              <p>Choose an existing task or create a new one.</p>

              <div className="task-picker-grid">
                {tasks.map((task) => (
                  <button key={task.id} className="task-picker-card" onClick={() => setSelectedTaskId(task.id)}>
                    <span className="task-picker-name">{task.name}</span>
                    <span className={`task-status status-${task.status}`}>
                      {task.status === 'thinking' ? 'â— Thinking' : task.lifecycle === 'closed' ? 'Closed' : task.status === 'stopped' ? 'â¸ Stopped' : 'Open'}
                    </span>
                  </button>
                ))}
              </div>
            </div>
          ) : (
            <div className="no-task-selected">
              <img className="no-task-hero" src={agentTaskImage} alt="AI agent hub connected to goals, questions, and task cards" />
              <div className="empty-state-badge">Local agent workspace</div>
              <h2>Give Dumb Barton a goal.</h2>
              <p>Create an agent task, then ask a question, define an objective, or give a concrete instruction. This is not just a chat window â€” it is a workspace for directing a local agent running on your machine.</p>
              <button className="btn btn-primary empty-state-button" onClick={createTask}>Create Agent Task</button>
            </div>
          )}
        </main>
      </div>
    </div>
  )
}

export default App

