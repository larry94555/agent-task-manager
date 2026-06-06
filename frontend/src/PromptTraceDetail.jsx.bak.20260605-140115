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
    <section className="trace-text-section">
      <div className="trace-text-heading">
        <div>
          <h4>{title}</h4>
          {subtitle && <p>{subtitle}</p>}
        </div>
        <button
          type="button"
          className="btn btn-secondary trace-toggle"
          onClick={() => setExpanded((current) => !current)}
        >
          {expanded ? 'Collapse' : 'Open full text'}
        </button>
      </div>
      <textarea
        className={`trace-textbox ${expanded ? 'expanded' : 'collapsed'}`}
        readOnly
        value={displayValue}
        rows={expanded ? 10 : 2}
        onClick={() => setExpanded(true)}
      />
    </section>
  )
}

export function PromptTraceDetail({ task, message, messageIndex, onBack }) {
  const traces = Array.isArray(message?.modelCallTraces) ? message.modelCallTraces : []

  return (
    <div className="trace-detail-view">
      <div className="trace-detail-header">
        <div>
          <button type="button" className="trace-back-link" onClick={onBack}>
            â† Back to Task
          </button>
          <h2>Prompt/Response Deep Dive</h2>
          <p>
            Task: <strong>{task?.name ?? 'Unknown task'}</strong>
            {Number.isFinite(Number(messageIndex)) ? ` Â· Agent message #${Number(messageIndex) + 1}` : ''}
          </p>
        </div>
        <div className="trace-summary-card">
          <span>{traces.length}</span>
          model call{traces.length === 1 ? '' : 's'}
        </div>
      </div>

      <TraceTextBox
        title="Final answer shown in the task"
        subtitle="This is the assistant message that the normal task view displays."
        value={message?.content}
      />

      {traces.length === 0 ? (
        <div className="trace-empty-state">
          No llama.cpp prompt trace was attached to this message. New responses will include traces after the backend patch is running.
        </div>
      ) : (
        traces.map((trace, index) => (
          <article key={`${trace.callNumber ?? index}-${trace.startedAt ?? index}`} className="trace-call-card">
            <div className="trace-call-title">
              <div>
                <h3>Model call {trace.callNumber || index + 1}</h3>
                <p>
                  {trace.messageCount ?? 0} message(s) Â· max_tokens {trace.maxTokens ?? '?'} Â· temperature {trace.temperature ?? '?'}
                </p>
              </div>
              <div className={`trace-status ${trace.success ? 'success' : 'failure'}`}>
                {trace.success ? 'success' : 'failed'}
                {trace.durationMs != null && ` Â· ${formatDuration(trace.durationMs)}`}
                {trace.httpStatus ? ` Â· HTTP ${trace.httpStatus}` : ''}
              </div>
            </div>

            {trace.error && (
              <div className="trace-error-box">
                {trace.error}
              </div>
            )}

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
        ))
      )}
    </div>
  )
}
