import { useRef, useState } from 'react'
import { parseScript } from './useScriptRunner'
import './ScriptRunner.css'

export function ScriptRunner({ onRun, scriptStatus }) {
  const fileInputRef = useRef(null)
  const [errors, setErrors] = useState([])
  const [fileName, setFileName] = useState(null)
  const [loading, setLoading] = useState(false)

  const handleFileChange = (e) => {
    const file = e.target.files?.[0]
    if (!file) return
    setFileName(file.name)
    setErrors([])

    const reader = new FileReader()
    reader.onload = (evt) => {
      const text = evt.target.result
      const { steps, errors: parseErrors } = parseScript(text)

      if (parseErrors.length > 0) {
        setErrors(parseErrors)
        return
      }
      if (steps.length === 0) {
        setErrors(['No valid steps found in script.'])
        return
      }

      setLoading(true)
      onRun(steps).finally(() => setLoading(false))
    }
    reader.readAsText(file)
    // Reset so the same file can be reloaded
    e.target.value = ''
  }

  return (
    <div className="script-runner">
      <div className="script-runner-header">
        Script Runner
      </div>

      <input
        ref={fileInputRef}
        type="file"
        accept=".csv,.txt"
        style={{ display: 'none' }}
        onChange={handleFileChange}
      />

      <button
        className="btn btn-load-script"
        onClick={() => fileInputRef.current?.click()}
        disabled={loading}
      >
        {loading ? '⏳ Running…' : '📂 Load Script'}
      </button>

      {fileName && !loading && (
        <div className="script-filename">{fileName}</div>
      )}

      {errors.length > 0 && (
        <div className="script-errors">
          <div className="script-errors-title">Script errors</div>
          {errors.map((e, i) => (
            <div key={i} className="script-error-item">{e}</div>
          ))}
        </div>
      )}

      {scriptStatus && (
        <div className={`script-status ${loading ? 'script-status--running' : ''}`}>
          {scriptStatus}
        </div>
      )}
    </div>
  )
}