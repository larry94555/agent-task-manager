import { useCallback, useRef } from 'react'

const VALID_ACTIONS = ['TASK', 'POST_AND_WAIT']

function unescapeParam(value) {
  return String(value ?? '')
    .replace(/\\r/g, '\r')
    .replace(/\\n/g, '\n')
    .replace(/\\t/g, '\t')
    .replace(/\\"/g, '"')
    .replace(/\\\\/g, '\\')
}

function parseParam(raw, key) {
  const re = new RegExp(`${key}\\s*=\\s*"((?:\\\\.|[^"\\\\])*)"`, 'i')
  const m = raw.match(re)
  return m ? unescapeParam(m[1]) : null
}

function parseScriptLine(line) {
  const line2 = line.trim()
  if (!line2 || line2.startsWith('#')) return null

  const parts = line2.split(/,(?=(?:[^"]*"[^"]*")*[^"]*$)/)
  if (parts.length < 2) return null

  const lineNum = parseInt(parts[0].trim(), 10)
  const action = parts[1].trim().toUpperCase()
  const rest = parts.slice(2).join(',')

  if (!VALID_ACTIONS.includes(action)) {
    return { error: `Unknown action "${action}" on line ${lineNum}` }
  }

  if (action === 'TASK') {
    const newName = parseParam(rest, 'NEW_NAME')
    const name = parseParam(rest, 'NAME')
    const ref = parseParam(rest, 'REF')

    if (!ref) return { error: `Line ${lineNum}: TASK requires REF=".."` }
    if (!newName && !name) return { error: `Line ${lineNum}: TASK requires NEW_NAME or NAME` }

    return { lineNum, action, newName, name, ref }
  }

  if (action === 'POST_AND_WAIT') {
    const text = parseParam(rest, 'TEXT')
    const ref = parseParam(rest, 'REF')

    if (!text) return { error: `Line ${lineNum}: POST_AND_WAIT requires TEXT=".."` }
    if (!ref) return { error: `Line ${lineNum}: POST_AND_WAIT requires REF=".."` }

    return { lineNum, action, text, ref }
  }

  return null
}

export function parseScript(csvText) {
  const lines = csvText.split('\n')
  const steps = []
  const errors = []

  for (const line of lines) {
    const parsed = parseScriptLine(line)
    if (parsed === null) continue

    if (parsed.error) {
      errors.push(parsed.error)
      continue
    }

    steps.push(parsed)
  }

  return { steps, errors }
}

export function makeUniqueName(desired, existingNames) {
  const lower = (s) => s.toLowerCase()
  const taken = new Set(existingNames.map(lower))

  if (!taken.has(lower(desired))) return desired

  let i = 2
  while (taken.has(lower(`${desired} (${i})`))) i++

  return `${desired} (${i})`
}

export function useScriptRunner({
  setTasks,
  nextTaskId,
  setNextTaskId,
  sendMessage,
  setScriptStatus,
}) {
  const refMap = useRef({})
  const nextTaskIdRef = useRef(nextTaskId)
  nextTaskIdRef.current = nextTaskId

  const runScript = useCallback(async (steps) => {
    refMap.current = {}

    for (const step of steps) {
      if (step.action === 'TASK') {
        const ref = step.ref

        if (step.newName) {
          setTasks((prev) => {
            const finalName = makeUniqueName(step.newName, prev.map((t) => t.name))
            const newId = nextTaskIdRef.current
            const newTask = {
              id: newId,
              name: finalName,
              messages: [],
              status: 'idle',
              lifecycle: 'open',
              createdBy: 'script',
              parentTaskId: null,
              createdAt: new Date().toISOString(),
              requestStartedAt: null,
              lastDurationMs: null,
            }

            refMap.current[ref] = newId
            return [...prev, newTask]
          })

          setNextTaskId((prev) => prev + 1)
          nextTaskIdRef.current += 1
          setScriptStatus(`Step ${step.lineNum}: created task`)
        } else if (step.name) {
          setTasks((prev) => {
            const found = prev.find((t) => t.name.toLowerCase() === step.name.toLowerCase())
            if (found) refMap.current[ref] = found.id
            return prev
          })

          setScriptStatus(`Step ${step.lineNum}: using task "${step.name}"`)
        }

        await new Promise((resolve) => setTimeout(resolve, 80))
      }

      if (step.action === 'POST_AND_WAIT') {
        const taskId = refMap.current[step.ref]

        if (taskId == null) {
          setScriptStatus(`Step ${step.lineNum}: ERROR â€” ref "${step.ref}" has no task`)
          continue
        }

        setScriptStatus(`Step ${step.lineNum}: posting to "${step.ref}"â€¦`)
        await sendMessage(taskId, step.text)
        setScriptStatus(`Step ${step.lineNum}: response received`)
      }
    }

    setScriptStatus('Script complete')
  }, [setTasks, setNextTaskId, sendMessage, setScriptStatus])

  return { runScript }
}

