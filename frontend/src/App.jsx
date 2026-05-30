import { useState, useRef, useCallback } from 'react'
import './App.css'

const API_URL = '/api/chat'

function Task({ task, onClose, onEditName, onSendMessage, onStop, onRestart }) {
  const [inputText, setInputText] = useState('')
  const [editingName, setEditingName] = useState(false)
  const [nameDraft, setNameDraft] = useState(task.name)
  const messagesEndRef = useRef(null)

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
            onClick={() => { setNameDraft(task.name); setEditingName(true) }}
            title="Click to edit name"
          >
            {task.name}
          </h3>
        )}
        <div className="task-actions">
          {task.status === 'thinking' && (
            <button className="btn btn-stop" onClick={() => onStop(task.id)} title="Stop thinking">
              ⏹ Stop
            </button>
          )}
          {task.status === 'stopped' && (
            <button className="btn btn-restart" onClick={() => onRestart(task.id)} title="Restart task">
              ▶ Restart
            </button>
          )}
          <button className="btn btn-close" onClick={() => onClose(task.id)} title="Close task">
            ✕ Close
          </button>
        </div>
      </div>

      <div className="messages">
        {task.messages.length === 0 && (
          <div className="empty-chat">No messages yet. Start the conversation!</div>
        )}
        {task.messages.map((msg, idx) => (
          <div key={idx} className={`message message-${msg.role}`}>
            <div className="message-label">{msg.role === 'user' ? 'You' : 'AI'}</div>
            <div className="message-content">{msg.content}</div>
          </div>
        ))}
        {task.status === 'thinking' && (
          <div className="message message-assistant thinking">
            <div className="message-label">AI</div>
            <div className="thinking-dots">
              <span>thinking</span>
              <span className="dot-pulse">...</span>
            </div>
          </div>
        )}
        <div ref={messagesEndRef} />
      </div>

      <div className="input-area">
        <textarea
          className="message-input"
          placeholder={task.status === 'thinking' ? 'Waiting for response...' : 'Type your message...'}
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
  const abortControllers = useRef({})

  const createTask = () => {
    const task = {
        id: nextTaskId,
        name: `Task ${nextTaskId}`,
        messages: [],
        status: 'idle' // idle | thinking | stopped | done
    }

    setTasks(prev => [...prev, task])
    setNextTaskId(prev => prev + 1)
    setSelectedTaskId(task.id)
  } 

  const closeTask = useCallback((taskId) => {
    setTasks(prev => prev.filter(t => t.id !== taskId))
    if (abortControllers.current[taskId]) {
      abortControllers.current[taskId].abort()
      delete abortControllers.current[taskId]
    }
    setSelectedTaskId(prev => prev === taskId ? null : prev)
  }, [])

  const editTaskName = useCallback((taskId, newName) => {
    setTasks(prev => prev.map(t =>
      t.id === taskId ? { ...t, name: newName } : t
    ))
  }, [])

  const sendMessage = useCallback(async (taskId, text) => {
    const userMessage = { role: 'user', content: text }

    setTasks(prev => prev.map(t =>
      t.id === taskId
        ? { ...t, messages: [...t.messages, userMessage], status: 'thinking' }
        : t
    ))

    const controller = new AbortController()
    abortControllers.current[taskId] = controller

    try {
      const response = await fetch(API_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ text }),
        signal: controller.signal
      })

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`)
      }

      const aiResponse = await response.text()
      const aiMessage = { role: 'assistant', content: aiResponse }

      setTasks(prev => prev.map(t =>
        t.id === taskId
          ? { ...t, messages: [...t.messages, aiMessage], status: 'done' }
          : t
      ))
    } catch (err) {
      if (err.name === 'AbortError') {
        setTasks(prev => prev.map(t =>
          t.id === taskId ? { ...t, status: 'stopped' } : t
        ))
      } else {
        const errorMessage = { role: 'assistant', content: `Error: ${err.message}` }
        setTasks(prev => prev.map(t =>
          t.id === taskId
            ? { ...t, messages: [...t.messages, errorMessage], status: 'done' }
            : t
        ))
      }
    } finally {
      delete abortControllers.current[taskId]
    }
  }, [])

  const stopThinking = useCallback((taskId) => {
    if (abortControllers.current[taskId]) {
      abortControllers.current[taskId].abort()
    }
  }, [])

  const restartTask = useCallback((taskId) => {
    setTasks(prev => prev.map(t =>
      t.id === taskId ? { ...t, status: 'idle' } : t
    ))
  }, [])

  const openTasks = tasks.filter(t => t.status !== 'closed')
  const selectedTask = tasks.find(t => t.id === selectedTaskId)

  return (
    <div className="app">
      <header className="app-header">
        <h1>SimpleAgent</h1>
        <div className="header-actions">
          <button className="btn btn-primary" onClick={createTask}>
            + New Task
          </button>
        </div>
      </header>

      <div className="main-layout">
        <aside className="task-list">
          <h2>Tasks</h2>
          {openTasks.length === 0 ? (
            <div className="no-tasks">No tasks yet. Create one to get started!</div>
          ) : (
            <ul>
              {openTasks.map(task => (
                <li
                  key={task.id}
                  className={`task-list-item ${task.id === selectedTaskId ? 'active' : ''}`}
                  onClick={() => setSelectedTaskId(task.id)}
                >
                  <span className="task-list-name">{task.name}</span>
                  <span className={`task-status status-${task.status}`}>
                    {task.status === 'thinking' ? '●' : task.status === 'stopped' ? '⏸' : ''}
                  </span>
                </li>
              ))}
            </ul>
          )}
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
          ) : (
            <div className="no-task-selected">
              <div className="no-task-icon">💬</div>
              <p>Select a task or create a new one to start chatting</p>
            </div>
          )}
        </main>
      </div>
    </div>
  )
}

export default App