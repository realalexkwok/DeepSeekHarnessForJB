/**
 * jb-bridge — runtime-side plugin installed via a composition `--patch` over the
 * built-in `sdk` profile. It gives the IDE two channels through a localhost HTTP
 * bridge (URL + token arrive via DSH_JB_BRIDGE_URL / DSH_JB_BRIDGE_TOKEN):
 *
 *  1. Answerer: claims `plan-review` questions on the `user-questions/request`
 *     waterfall, forwards them to the IDE bridge (`POST /answer`), and returns
 *     the user's decision so it lands in the calling tool's result.
 *     Fail-closed: any failure falls through to the harness default (unavailable).
 *  2. Command relay: polls the IDE bridge (`GET /commands`) and executes queued
 *     commands (`/plan <message>`, `/plan off`, …) through the commands registry.
 */
export const name = 'jb-bridge'

const BRIDGE_URL = process.env.DSH_JB_BRIDGE_URL
const BRIDGE_TOKEN = process.env.DSH_JB_BRIDGE_TOKEN
const ANSWER_TIMEOUT_MS = Number(process.env.DSH_JB_BRIDGE_TIMEOUT_MS ?? 120_000)
const COMMAND_POLL_MS = Number(process.env.DSH_JB_COMMAND_POLL_MS ?? 300)

async function callBridge(path, payload, timeoutMs) {
  if (!BRIDGE_URL || !BRIDGE_TOKEN) return null
  try {
    const res = await fetch(`${BRIDGE_URL}${path}`, {
      method: payload === null ? 'GET' : 'POST',
      headers: { 'content-type': 'application/json', authorization: `Bearer ${BRIDGE_TOKEN}` },
      body: payload === null ? undefined : JSON.stringify(payload),
      signal: AbortSignal.timeout(timeoutMs),
    })
    if (!res.ok) return null
    return await res.json()
  } catch {
    return null
  }
}

/** Owned agents: the runtime serves one session per SDK connection. */
function firstAgent(ctx) {
  const registry = ctx.get('agents')
  const list = typeof registry?.list === 'function' ? registry.list() : []
  return list[0]
}

export function apply(ctx) {
  // TEMP diagnostics (item 8 bring-up): trace the relay chain on stderr.
  const diag = (...parts) => console.error('[jb-bridge]', ...parts)
  diag('apply start', Boolean(BRIDGE_URL), Boolean(BRIDGE_TOKEN))
  if (!BRIDGE_URL || !BRIDGE_TOKEN) return

  // 1) Plan-review answerer.
  ctx.on('user-questions/request', async (request, next) => {
    const questions = request?.questions ?? []
    const hasPlanReview = questions.some(q => q?.intent?.kind === 'plan-review')
    if (!hasPlanReview) return next()
    const reply = await callBridge('/answer', { questions }, ANSWER_TIMEOUT_MS)
    if (reply && Array.isArray(reply.answers)) return reply // { answers: [{ id, selected, custom? }] }
    return next() // fail closed, like the harness default
  })

  // 2) Command relay: poll the IDE queue and execute on the owned agent.
  let tick = 0
  const timer = setInterval(async () => {
    tick += 1
    if (tick > 30) return // TEMP: stop logging after ~9s
    const commands = ctx.get('commands')
    if (!commands || typeof commands.execute !== 'function') {
      diag('tick', tick, 'no-commands')
      return
    }
    const agent = firstAgent(ctx)
    if (!agent) {
      diag('tick', tick, 'no-agent', 'agents=', String(ctx.get('agents')))
      return
    }
    diag('tick', tick, 'agent-found')
    try {
      const queued = await callBridge('/commands', null, 2_000)
      diag('tick', tick, 'poll', JSON.stringify(queued))
      if (!queued || !Array.isArray(queued.commands)) return
      diag('queued commands', queued.commands.length)
      for (const entry of queued.commands) {
        if (typeof entry?.line !== 'string') continue
        try {
          const result = await commands.execute(agent, entry.line, [], new AbortController().signal)
          diag('executed', entry.line, result?.result?.kind ?? 'no-result')
        } catch (e) {
          diag('execute failed', entry.line, String(e))
        }
      }
    } catch (e) {
      diag('poll failed', String(e))
    }
  }, COMMAND_POLL_MS)
  timer.unref?.()

  ctx.on('dispose', () => clearInterval(timer))
}
