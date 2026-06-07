import { useState } from 'react'

function formatDuration(ms) {
  if (ms == null || Number.isNaN(Number(ms))) return ''
  const numeric = Number(ms)
  if (numeric < 1000) return `${Math.max(0, Math.round(numeric))} ms`
  if (numeric < 60_000) return `${(numeric / 1000).toFixed(1)} s`
  const minutes = Math.floor(numeric / 60_000)
  const seconds = Math.round((numeric % 60_000) / 1000)
  return `${minutes}m ${seconds}s`
}

function previewText(value, maxChars = 80) {
  const text = String(value ?? '')
  const normalized = text.replace(/\s+/g, ' ').trim()
  if (!normalized) return '(empty)'
  return normalized.length > maxChars ? `${normalized.slice(0, maxChars)}...` : normalized
}

function TraceTextBox({ value }) {
  const [expanded, setExpanded] = useState(false)
  const text = String(value ?? '')
  const displayValue = expanded ? text : previewText(text)
  return (
    <section className="trace-text-box">
      <div className="trace-text-heading">
        <div className="trace-text-meta" />
        <button
          type="button"
          className="trace-toggle"
          onClick={() => setExpanded((current) => !current)}
        >
          {expanded ? 'Collapse' : 'Open full text'}
        </button>
      </div>
      <textarea
        className="trace-textarea"
        value={displayValue}
        readOnly
        spellCheck={false}
        rows={expanded ? 16 : 4}
        onFocus={(event) => event.currentTarget.select()}
      />
    </section>
  )
}

function SectionLabel({ children }) {
  return <div className="trace-section-label">{children}</div>
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

function PlanTraceCard({ trace, index }) {
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
      <TraceTextBox value={trace?.observation} />

      <SectionLabel>Plan JSON</SectionLabel>
      <TraceTextBox value={trace?.input} />
    </article>
  )
}

function ToolTraceCard({ trace, index }) {
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
        <div className="trace-goal-box">
          Goal: {trace.planStepGoal}
        </div>
      )}

      <SectionLabel>Action Input</SectionLabel>
      <TraceTextBox value={trace?.input} />

      <SectionLabel>Tool Observation Returned To The Agent Loop</SectionLabel>
      <TraceTextBox value={trace?.observation} />
    </article>
  )
}

export function PromptTraceDetail({ task, message, messageIndex, onBack }) {
  const traces = Array.isArray(message?.modelCallTraces) ? message.modelCallTraces : []
  const webToolTraces = Array.isArray(message?.webToolTraces) ? message.webToolTraces : []
  const planTraces = webToolTraces.filter((trace) => trace?.traceType === 'plan' || trace?.action === 'execution_plan')
  const toolTraces = webToolTraces.filter((trace) => !(trace?.traceType === 'plan' || trace?.action === 'execution_plan'))
  const originalPrompt = findOriginalPrompt(task, message, messageIndex)

  return (
    <div className="trace-detail-view">
      <div className="trace-detail-header">
        <div>
          <button type="button" className="trace-back-link" onClick={onBack}>
            Back to Task
          </button>
          <h2>Prompt/Response Deep Dive</h2>
          <p>
            Task: <strong>{task?.name ?? 'Unknown task'}</strong>
          </p>
        </div>
        <div className="trace-summary-card">
          <span>{traces.length}</span> model call{traces.length === 1 ? '' : 's'}
          {webToolTraces.length > 0 && (
            <small>{webToolTraces.length} tool action{webToolTraces.length === 1 ? '' : 's'}</small>
          )}
        </div>
      </div>

      <SectionLabel>Original Prompt</SectionLabel>
      <TraceTextBox value={originalPrompt || 'Original prompt was not captured for this message.'} />

      <SectionLabel>Final Response</SectionLabel>
      <TraceTextBox value={message?.content} />

      {planTraces.length > 0 && (
        <section className="trace-tool-section">
          <h2>Visible Execution Plan</h2>
          <p>
            The plan is generated before tool execution. Each plan step should correspond to a tool action below with the
            same Step ID.
          </p>
          {planTraces.map((trace, index) => (
            <PlanTraceCard key={`${trace.planId ?? index}-${trace.startedAt ?? index}`} trace={trace} index={index} />
          ))}
        </section>
      )}

      {toolTraces.length > 0 && (
        <section className="trace-tool-section">
          <h2>Plan/Tool Actions</h2>
          <p>These are the executed actions. Plan-backed actions use numbering such as Step 1.1 and Step 1.2.</p>
          {toolTraces.map((trace, index) => (
            <ToolTraceCard key={`${trace.displayStep ?? trace.step ?? index}-${trace.startedAt ?? index}`} trace={trace} index={index} />
          ))}
        </section>
      )}

      {traces.length === 0 ? (
        <div className="trace-empty-state">
          No llama.cpp prompt trace was attached to this message. New responses will include traces after the backend patch is
          running.
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
                <TraceTextBox value={trace.prompt} />

                <SectionLabel>Raw Response Returned By llama.cpp</SectionLabel>
                <TraceTextBox value={trace.response} />

                <SectionLabel>Extracted Assistant Content</SectionLabel>
                <TraceTextBox value={trace.extractedContent} />
              </article>
            )
          })}
        </section>
      )}
    </div>
  )
}