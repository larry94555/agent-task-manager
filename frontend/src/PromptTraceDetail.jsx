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

function TraceTextBox({ title, subtitle, value }) {
  const [expanded, setExpanded] = useState(false)
  const text = String(value ?? '')
  const displayValue = expanded ? text : previewText(text)
  return (
    <section className="trace-text-box">
      <div className="trace-text-header">
        <div>
          <h4>{title}</h4>
          {subtitle && <p>{subtitle}</p>}
        </div>
        <button type="button" onClick={() => setExpanded((current) => !current)}>
          {expanded ? 'Collapse' : 'Open full text'}
        </button>
      </div>
      <textarea readOnly value={displayValue} onFocus={() => setExpanded(true)} />
    </section>
  )
}

function PlanTraceCard({ trace, index }) {
  return (
    <article className="trace-call-card plan-trace-card">
      <div className="trace-call-title">
        <div>
          <h3>{trace?.displayStep || trace?.planId || `Plan ${index + 1}`}: {trace?.planTitle || 'Execution plan'}</h3>
          <p>Visible plan summary generated before tool execution.</p>
        </div>
        <div className="trace-status success">plan</div>
      </div>
      <TraceTextBox
        title="Plan summary"
        subtitle="Every plan step below should map to a tool action with the same Step ID, such as Step 1.1 or Step 1.2."
        value={trace?.observation}
      />
      <TraceTextBox
        title="Plan JSON"
        subtitle="Structured plan data used by the backend before executing tool actions."
        value={trace?.input}
      />
    </article>
  )
}

function ToolTraceCard({ trace, index }) {
  const displayStep = trace?.displayStep || (trace?.planStepId ? `Step ${trace.planStepId}` : `Tool action ${index + 1}`)
  const status = trace?.success ? 'success' : 'failure'
  return (
    <article className="trace-call-card">
      <div className="trace-call-title">
        <div>
          <h3>{displayStep}: {trace?.action || '(unknown action)'}</h3>
          <p>
            {trace?.planId ? `${trace.planId}` : 'Ad-hoc action'}
            {trace?.planStepTitle ? ` | ${trace.planStepTitle}` : ''}
            {trace?.errorCode ? ` | ${trace.errorCode}` : ''}
          </p>
        </div>
        <div className={`trace-status ${status}`}>
          {trace?.success ? 'success' : 'failed'}
          {trace?.durationMs != null && ` | ${formatDuration(trace.durationMs)}`}
        </div>
      </div>
      {trace?.planStepGoal && (
        <div className="trace-error-box">
          Goal: {trace.planStepGoal}
        </div>
      )}
      <TraceTextBox
        title="Action input"
        subtitle="JSON input selected by the model or visible plan for this tool action."
        value={trace?.input}
      />
      <TraceTextBox
        title="Tool observation returned to the agent loop"
        subtitle="Exact observation added back to the agent conversation after the tool finished."
        value={trace?.observation}
      />
    </article>
  )
}

export function PromptTraceDetail({ task, message, messageIndex, onBack }) {
  const traces = Array.isArray(message?.modelCallTraces) ? message.modelCallTraces : []
  const webToolTraces = Array.isArray(message?.webToolTraces) ? message.webToolTraces : []
  const planTraces = webToolTraces.filter((trace) => trace?.traceType === 'plan' || trace?.action === 'execution_plan')
  const toolTraces = webToolTraces.filter((trace) => !(trace?.traceType === 'plan' || trace?.action === 'execution_plan'))

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
            {Number.isFinite(Number(messageIndex)) ? ` | Agent message #${Number(messageIndex) + 1}` : ''}
          </p>
        </div>
        <div className="trace-summary-card">
          <span>{traces.length}</span> model call{traces.length === 1 ? '' : 's'}
          {webToolTraces.length > 0 && (
            <small>{webToolTraces.length} tool action{webToolTraces.length === 1 ? '' : 's'}</small>
          )}
        </div>
      </div>

      <TraceTextBox
        title="Final answer shown in the task"
        subtitle="This is the assistant message that the normal task view displays."
        value={message?.content}
      />

      {planTraces.length > 0 && (
        <section className="trace-tool-section">
          <h2>Visible execution plan</h2>
          <p>The plan is generated before tool execution. Each plan step should correspond to a tool action below with the same Step ID.</p>
          {planTraces.map((trace, index) => (
            <PlanTraceCard key={`${trace.planId ?? index}-${trace.startedAt ?? index}`} trace={trace} index={index} />
          ))}
        </section>
      )}

      {toolTraces.length > 0 && (
        <section className="trace-tool-section">
          <h2>Plan/tool actions</h2>
          <p>These are the executed actions. Plan-backed actions use numbering such as Step 1.1 and Step 1.2.</p>
          {toolTraces.map((trace, index) => (
            <ToolTraceCard key={`${trace.displayStep ?? trace.step ?? index}-${trace.startedAt ?? index}`} trace={trace} index={index} />
          ))}
        </section>
      )}

      {traces.length === 0 ? (
        <div className="trace-empty-state">
          No llama.cpp prompt trace was attached to this message.
          New responses will include traces after the backend patch is running.
        </div>
      ) : (
        <section className="trace-model-section">
          <h2>Model calls</h2>
          {traces.map((trace, index) => (
            <article key={`${trace.callNumber ?? index}-${trace.startedAt ?? index}`} className="trace-call-card">
              <div className="trace-call-title">
                <div>
                  <h3>Model call {trace.callNumber || index + 1}</h3>
                  <p>
                    {trace.messageCount ?? 0} message(s) | max_tokens {trace.maxTokens ?? '?'} | temperature {trace.temperature ?? '?'}
                  </p>
                </div>
                <div className={`trace-status ${trace.success ? 'success' : 'failure'}`}>
                  {trace.success ? 'success' : 'failed'}
                  {trace.durationMs != null && ` | ${formatDuration(trace.durationMs)}`}
                  {trace.httpStatus ? ` | HTTP ${trace.httpStatus}` : ''}
                </div>
              </div>
              {trace.error && <div className="trace-error-box">{trace.error}</div>}
              <TraceTextBox
                title="Prompt sent to llama.cpp"
                subtitle="Exact JSON request body sent to /v1/chat/completions."
                value={trace.prompt}
              />
              <TraceTextBox
                title="Raw response returned by llama.cpp"
                subtitle="Exact HTTP response body captured before content extraction."
                value={trace.response}
              />
              <TraceTextBox
                title="Extracted assistant content"
                subtitle="The choices[0].message.content value used by the agent loop."
                value={trace.extractedContent}
              />
            </article>
          ))}
        </section>
      )}
    </div>
  )
}