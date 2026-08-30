/**
 * jb-bridge — runtime-side plugin installed via a composition `--patch` over the
 * built-in `sdk` profile. It gives the IDE three channels through a localhost HTTP
 * bridge (URL + token arrive via DSH_JB_BRIDGE_URL / DSH_JB_BRIDGE_TOKEN):
 *
 *  1. Answerer: claims ALL `user-questions/request` intents on the waterfall
 *     (plan reviews AND generic asks — item 9), forwards them to the IDE bridge
 *     (`POST /answer`), and returns the user's decision so it lands in the
 *     calling tool's result. Fail-closed: any failure falls through to the
 *     harness default (unavailable).
 *  2. Approver (item 9): claims `approval/request` and forwards the request
 *     (toolName, callId, reason) to the IDE bridge (`POST /approval`), returning
 *     the decided outcome. Fail-closed: `next()` delegates to the harness default.
 *  3. Command relay: polls the IDE bridge (`GET /commands`) and executes queued
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
  const diag = (...parts) => console.error('[jb-bridge]', ...parts)
  diag('apply start', Boolean(BRIDGE_URL), Boolean(BRIDGE_TOKEN))

  // 0) Register the model-facing ask_user_question tool (item 9 fix round): the
  // built-in `sdk` profile composes the user-questions SERVICE but NOT the
  // `tool-ask-user` package, so the model has no way to ask. The definition
  // mirrors @deepseek-ai/dsh-tool-ask-user exactly (plain registry object —
  // defineTool is only a normalizer, and package imports are not resolvable
  // from this patch-loaded file). Registration is RETRIED from the poll timer
  // because the services may not be mounted yet at apply time.
  let askToolRegistered = false
  const registerAskTool = () => {
    if (askToolRegistered) return
    const userQuestions = ctx.get('userQuestions')
    const tools = ctx.get('tools')
    if (!userQuestions || !tools || typeof tools.register !== 'function') return
    tools.register({
      name: 'ask_user_question',
      description: 'Ask the user a concise question when you need confirmation, a choice, or missing information before proceeding. '
        + 'Send one or more questions, each with a stable id that will be echoed in the answer.',
      parameters: {
        type: 'object',
        required: ['questions'],
        properties: {
          questions: {
            type: 'array',
            description: 'Questions to ask the user before continuing.',
            items: {
              type: 'object',
              required: ['id', 'question'],
              properties: {
                id: { type: 'string', description: 'Stable id for this question; echoed in the answer.' },
                question: { type: 'string', description: 'The specific question to ask the user.' },
                header: { type: 'string', description: 'Optional short heading for the question, such as "Confirm" or "Choose Mode".' },
                options: {
                  type: 'array',
                  description: 'Optional choices to show the user. If you recommend one, put it first and append "(Recommended)" to that label.',
                  items: {
                    type: 'object',
                    required: ['label'],
                    properties: {
                      label: { type: 'string', description: 'Short user-facing option label.' },
                      description: { type: 'string', description: 'One sentence explaining the tradeoff or impact.' },
                    },
                  },
                },
                multi_select: { type: 'boolean', description: 'Whether the user may select more than one option. Defaults to false.' },
              },
            },
          },
        },
      },
      output: {
        schema: {
          type: 'object',
          required: ['answers'],
          properties: {
            answers: {
              type: 'array',
              items: {
                type: 'object',
                required: ['id', 'selected'],
                properties: {
                  id: { type: 'string' },
                  selected: { type: 'array', items: { type: 'string' } },
                  custom: { type: 'string' },
                },
              },
            },
          },
        },
        render: (_args, value) => [{ type: 'text', text: JSON.stringify(value) }],
      },
      async execute(args, exec) {
        const result = await userQuestions.ask({
          questions: args.questions.map(question => ({
            id: question.id,
            question: question.question,
            ...question.header !== undefined ? { header: question.header } : {},
            ...question.options !== undefined ? { options: question.options } : {},
            ...question.multi_select !== undefined ? { multiSelect: question.multi_select } : {},
          })),
          ...exec.agent !== undefined ? { agent: exec.agent } : {},
          signal: exec.signal,
        })
        return {
          answers: result.answers.map(answer => ({
            id: answer.id,
            selected: [...answer.selected],
            ...answer.custom !== undefined ? { custom: answer.custom } : {},
          })),
        }
      },
    })
    askToolRegistered = true
    diag('ask_user_question tool registered')
  }
  registerAskTool()

  if (!BRIDGE_URL || !BRIDGE_TOKEN) return

  // 1) Generic question answerer (plan-review AND other intents, item 9).
  ctx.on('user-questions/request', async (request, next) => {
    const questions = request?.questions ?? []
    if (questions.length === 0) return next()
    const reply = await callBridge('/answer', { questions }, ANSWER_TIMEOUT_MS)
    if (reply && Array.isArray(reply.answers)) return reply // { answers: [{ id, selected, custom? }] }
    return next() // fail closed, like the harness default
  })

  // 2) Tool-approval answerer (item 9).
  ctx.on('approval/request', async (request, next) => {
    const reply = await callBridge('/approval', {
      toolName: request?.toolName,
      callId: request?.callId,
      reason: request?.reason,
    }, ANSWER_TIMEOUT_MS)
    if (reply && typeof reply.outcome === 'string') return reply.outcome
    return next() // fail closed: the harness normalizes to 'unavailable'
  })

  // 3) Command relay: poll the IDE queue and execute on the owned agent.
  let tick = 0
  const timer = setInterval(async () => {
    tick += 1
    if (tick > 30) return // TEMP: stop logging after ~9s
    registerAskTool()
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
