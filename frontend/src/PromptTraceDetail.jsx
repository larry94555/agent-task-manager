import { useMemo, useRef, useState } from 'react'

const UI_SETTINGS_STORAGE_KEY = 'dumb-barton-ui-settings'
const DEFAULT_DEEP_DIVE_PREVIEW_CHARS = 160

function normalizeText(value) {
  return String(value ?? '')
}

function normalizeUiSettings(settings) {
  const raw = Number(settings?.deepDivePreviewChars)
  const deepDivePreviewChars = Number.isFinite(raw)
    ? Math.min(600, Math.max(40, Math.round(raw)))
    : DEFAULT_DEEP_DIVE_PREVIEW_CHARS
  return { deepDivePreviewChars }
}

function readUiSettings() {
  try {
    const raw = window.localStorage.getItem(UI_SETTINGS_STORAGE_KEY)
    if (!raw) return normalizeUiSettings({})
    return normalizeUiSettings(JSON.parse(raw))
  } catch {
    return normalizeUiSettings({})
  }
}

function writeUiSettings(settings) {
  const normalized = normalizeUiSettings(settings)
  window.localStorage.setItem(UI_SETTINGS_STORAGE_KEY, JSON.stringify(normalized))
  return normalized
}

function formatDuration(ms) {
  if (ms == null || Number.isNaN(Number(ms))) return ''
  const numeric = Number(ms)
  if (numeric < 1000) return `${Math.max(0, Math.round(numeric))} ms`
  if (numeric < 60_000) return `${(numeric / 1000).toFixed(1)} s`
  const minutes = Math.floor(numeric / 60_000)
  const seconds = Math.round((numeric % 60_000) / 1000)
  return `${minutes}m ${seconds}s`
}

function previewText(value, maxChars = DEFAULT_DEEP_DIVE_PREVIEW_CHARS) {
  const text = normalizeText(value)
  const normalized = text.replace(/\s+/g, ' ').trim()
  if (!normalized) return '(empty)'
  return normalized.length > maxChars ? `${normalized.slice(0, maxChars)}...` : normalized
}

function SectionLabel({ children }) {
  return <div className="trace-plain-label">{children}</div>
}

function SettingsDialog({ open, draftValue, onDraftChange, onCancel, onKeep }) {
  if (!open) return null
  return (
    <div className="trace-settings-backdrop" role="dialog" aria-modal="true" aria-label="Deep View settings">
      <div className="trace-settings-dialog">
        <h3>Settings</h3>
        <label className="trace-settings-field">
          <span>Deep View preview length</span>
          <input
            type="number"
            min="40"
            max="600"
            step="10"
            value={draftValue}
            onChange={(event) => onDraftChange(event.target.value)}
          />
        </label>
        <div className="trace-settings-actions">
          <button type="button" className="btn btn-secondary" onClick={onCancel}>Cancel</button>
          <button type="button" className="btn btn-primary" onClick={onKeep}>Keep</button>
        </div>
      </div>
    </div>
  )
}

function TraceTextBox({ value, previewChars }) {
  const [expanded, setExpanded] = useState(false)
  const textareaRef = useRef(null)
  const text = normalizeText(value)
  const preview = useMemo(() => previewText(text, previewChars), [text, previewChars])
  const isTruncated = !expanded && preview !== text && preview.endsWith('...')
  const displayValue = expanded ? text : preview

  function toggleExpanded() {
    setExpanded((current) => !current)
  }

  function openExpanded() {
    setExpanded(true)
  }

  function handleTextareaMouseUp() {
    if (expanded || !isTruncated) return
    const textarea = textareaRef.current
    if (!textarea) return
    const caret = Number(textarea.selectionStart)
    if (!Number.isFinite(caret)) return
    const ellipsisStart = Math.max(0, displayValue.length - 3)
    if (caret >= ellipsisStart) {
      setExpanded(true)
    }
  }

  function handleTextareaKeyDown(event) {
    if (expanded || !isTruncated) return
    const selectionStart = Number(event.currentTarget.selectionStart)
    if (
      (event.key === 'Enter' || event.key === ' ') &&
      selectionStart >= Math.max(0, displayValue.length - 3)
    ) {
      event.preventDefault()
      setExpanded(true)
    }
  }

  return (
    <section className="trace-textbox-shell">
      <textarea
        ref={textareaRef}
        className="trace-text-box"
        readOnly
        value={displayValue}
        rows={expanded ? 16 : 7}
        onFocus={(event) => event.currentTarget.select()}
        onMouseUp={handleTextareaMouseUp}
        onKeyDown={handleTextareaKeyDown}
      />
      <button type="button" className="trace-toggle trace-toggle-attached" onClick={expanded ? toggleExpanded : openExpanded}>
        {expanded ? 'Collapse' : 'Open full text'}
      </button>
    </section>
  )
}

function findOriginalPrompt(task, message, messageIndex) {
  if (typeof message?.originalPrompt === 'string' && message.originalPrompt.trim()) {
    return message.originalPrompt
  }
  const messages = Array.isArray(task?.messages) ? task.messages : []
  const index = Number(messageIndex)
  if (Number.isFinite(index)) {
    for (let i = Math.min(index - 1, messages.length - 1); i >= 0; i -= 1) {
      const candidate = messages[i]
      if (candidate?.role === 'user' && typeof candidate.content === 'string' && candidate.content.trim()) {
        return candidate.content
      }
    }
  }
  return ''
}

function PlanTraceCard({ trace, index, previewChars }) {
  const planLabel = trace?.displayStep || trace?.planId || `Plan ${index + 1}`
  const title = trace?.planTitle || 'Execution plan'
  return (
    <article className="trace-call-card plan-trace-card">
      <div className="trace-call-title">
        <div>
          <h3>{planLabel}: {title}</h3>
          <p>Visible plan summary generated before tool execution.</p>
        </div>
        <div className="trace-status success">plan</div>
      </div>
      <SectionLabel>Plan Summary</SectionLabel>
      <TraceTextBox value={trace?.observation} previewChars={previewChars} />
      <SectionLabel>Plan JSON</SectionLabel>
      <TraceTextBox value={trace?.input} previewChars={previewChars} />
    </article>
  )
}

function ToolTraceCard({ trace, index, previewChars }) {
  const displayStep = trace?.displayStep || (trace?.planStepId ? `Step ${trace.planStepId}` : `Tool action ${index + 1}`)
  const status = trace?.success ? 'success' : 'failure'
  const meta = [trace?.planId || 'Ad-hoc action', trace?.planStepTitle, trace?.errorCode]
    .filter(Boolean)
    .join(' | ')
  const statusText = [trace?.success ? 'success' : 'failed', trace?.durationMs != null ? formatDuration(trace.durationMs) : null]
    .filter(Boolean)
    .join(' | ')
  return (
    <article className="trace-call-card">
      <div className="trace-call-title">
        <div>
          <h3>{displayStep}: {trace?.action || '(unknown action)'}</h3>
          {meta && <p>{meta}</p>}
        </div>
        <div className={`trace-status ${status}`}>{statusText}</div>
      </div>
      {trace?.planStepGoal && (
        <div className="trace-goal-box">Goal: {trace.planStepGoal}</div>
      )}
      <SectionLabel>Action Input</SectionLabel>
      <TraceTextBox value={trace?.input} previewChars={previewChars} />
      <SectionLabel>Tool Observation Returned To The Agent Loop</SectionLabel>
      <TraceTextBox value={trace?.observation} previewChars={previewChars} />
    </article>
  )
}

export function PromptTraceDetail({ task, message, messageIndex, onBack }) {
  const traces = Array.isArray(message?.modelCallTraces) ? message.modelCallTraces : []
  const webToolTraces = Array.isArray(message?.webToolTraces) ? message.webToolTraces : []
  const planTraces = webToolTraces.filter((trace) => trace?.traceType === 'plan' || trace?.action === 'execution_plan')
  const toolTraces = webToolTraces.filter((trace) => !(trace?.traceType === 'plan' || trace?.action === 'execution_plan'))
  const originalPrompt = findOriginalPrompt(task, message, messageIndex)

  const [uiSettings, setUiSettings] = useState(readUiSettings)
  const [settingsOpen, setSettingsOpen] = useState(false)
  const [settingsDraft, setSettingsDraft] = useState(() => String(readUiSettings().deepDivePreviewChars))

  const previewChars = uiSettings.deepDivePreviewChars

  function openSettingsDialog() {
    setSettingsDraft(String(uiSettings.deepDivePreviewChars))
    setSettingsOpen(true)
  }

  function closeSettingsDialog() {
    setSettingsDraft(String(uiSettings.deepDivePreviewChars))
    setSettingsOpen(false)
  }

  function keepSettingsDialog() {
    const next = writeUiSettings({ deepDivePreviewChars: settingsDraft })
    setUiSettings(next)
    setSettingsDraft(String(next.deepDivePreviewChars))
    setSettingsOpen(false)
  }

  return (
    <div className="trace-detail-view">
      <div className="trace-detail-header">
        <div>
          <button type="button" className="trace-back-link" onClick={onBack}>Back to Task</button>
          <h2>Prompt/Response Deep Dive</h2>
          <p>Task: <strong>{task?.name ?? 'Unknown task'}</strong></p>
        </div>
        <div className="trace-header-actions">
          <div className="trace-summary-card">
            <span>{traces.length}</span> model call{traces.length === 1 ? '' : 's'}
            {webToolTraces.length > 0 && (
              <small>{webToolTraces.length} tool action{webToolTraces.length === 1 ? '' : 's'}</small>
            )}
          </div>
        </div>
      </div>

      <SectionLabel>Original Prompt</SectionLabel>
      <TraceTextBox value={originalPrompt || 'Original prompt was not captured for this message.'} previewChars={previewChars} />

      <SectionLabel>Final Response</SectionLabel>
      <TraceTextBox value={message?.content} previewChars={previewChars} />

      {planTraces.length > 0 && (
        <section className="trace-tool-section">
          <h2>Visible Execution Plan</h2>
          <p>Each plan step should correspond to a tool action below with the same Step ID.</p>
          {planTraces.map((trace, index) => (
            <PlanTraceCard key={`${trace.planId ?? index}-${trace.startedAt ?? index}`} trace={trace} index={index} previewChars={previewChars} />
          ))}
        </section>
      )}

      {toolTraces.length > 0 && (
        <section className="trace-tool-section">
          <h2>Plan/Tool Actions</h2>
          <p>Plan-backed actions use numbering such as Step 1.1 and Step 1.2.</p>
          {toolTraces.map((trace, index) => (
            <ToolTraceCard key={`${trace.displayStep ?? trace.step ?? index}-${trace.startedAt ?? index}`} trace={trace} index={index} previewChars={previewChars} />
          ))}
        </section>
      )}

      {traces.length === 0 ? (
        <div className="trace-empty-state">
          No llama.cpp prompt trace was attached to this message. New responses will include traces after the backend patch is running.
        </div>
      ) : (
        <section className="trace-model-section">
          <h2>Model Calls</h2>
          {traces.map((trace, index) => {
            const meta = [
              `${trace.messageCount ?? 0} message(s)`,
              `max_tokens ${trace.maxTokens ?? '?'}`,
              `temperature ${trace.temperature ?? '?'}`,
            ].join(' | ')
            const statusText = [
              trace.success ? 'success' : 'failed',
              trace.durationMs != null ? formatDuration(trace.durationMs) : null,
              trace.httpStatus ? `HTTP ${trace.httpStatus}` : null,
            ]
              .filter(Boolean)
              .join(' | ')
            return (
              <article key={`${trace.callNumber ?? index}-${trace.startedAt ?? index}`} className="trace-call-card">
                <div className="trace-call-title">
                  <div>
                    <h3>Model call {trace.callNumber || index + 1}</h3>
                    <p>{meta}</p>
                  </div>
                  <div className={`trace-status ${trace.success ? 'success' : 'failure'}`}>{statusText}</div>
                </div>
                {trace.error && <div className="trace-error-box">{trace.error}</div>}
                <SectionLabel>Prompt Sent To llama.cpp</SectionLabel>
                <TraceTextBox value={trace.prompt} previewChars={previewChars} />
                <SectionLabel>Raw Response Returned By llama.cpp</SectionLabel>
                <TraceTextBox value={trace.response} previewChars={previewChars} />
                <SectionLabel>Extracted Assistant Content</SectionLabel>
                <TraceTextBox value={trace.extractedContent} previewChars={previewChars} />
              </article>
            )
          })}
        </section>
      )}

      <SettingsDialog
        open={settingsOpen}
        draftValue={settingsDraft}
        onDraftChange={setSettingsDraft}
        onCancel={closeSettingsDialog}
        onKeep={keepSettingsDialog}
      />
    </div>
  )
}