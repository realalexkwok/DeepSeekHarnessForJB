# Plan: Composer tabs, model/effort selection, settings polish

Task groups, in execution order.

A. Feature spec files (this folder) + annotations: roadmap items 6/11 pulled
   forward, tech-stack (runtime pool, model discovery, composer tabs), chat-window
   spec (composer supersedes the plain input), settings spec (bundled-exe label +
   node indicator).
B. Bundled `agent.cordis.yml` resource (copy of the SDK's own composition) +
   `CordisEffort.apply(base, effort)` patcher (pure string function, unit-tested).
C. `EffortLevel` (Off/Low/High/Max + wire ids) and `RuntimeKey(model, effort)` +
   `nodeVersion()`/`isNodeAvailable()` helpers in the runtime package.
D. `ModelCatalog`: known catalog + `parseModels(json)` (pure) + `fetch` via JDK
   HttpClient with keychain key; fallback to the known catalog.
E. `PromptAssembly` (pure): section assembly + action instructions; unit-tested.
F. `DshRuntimeService` pool rework: `startFor(key)` closes the previous runtime,
   writes the per-effort cordis file, spawns + initializes with the key's model;
   `prompt(text)` uses the active runtime; listeners re-attach per spawn.
G. Settings: `effort` persisted field; bundled-exe description label; Node.js
   status probe in the configurable.
H. `DshChatPanel` composer rework: tab strip + icon submit, context gathering from
   the editor + project AGENTS.md, model/effort switching with notices, catalog load
   on init, Settings… deep-link.
I. Unit tests: cordis patcher, prompt assembly, models parsing, node validation.
J. Verification: `./gradlew test`, `./gradlew buildPlugin`, env grep; results in
   `validation.md`.
