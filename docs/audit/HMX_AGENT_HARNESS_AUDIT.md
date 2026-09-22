# HMX ANDROID AI CODING IDE — FORENSIC CODEBASE + AGENT HARNESS AUDIT

> Repo: `/mnt/sdcard/AIProjects/normal1` (`hmx-ide`, AndroidIDE fork, GPLv3).
> Method: direct source reads + grep + file counts. No code modified. No Gradle build executed (requires device SDK).
> External harnesses (Cline / OpenCode / OpenHands) are NOT vendored in this repo — comparison is against public architectures.
> Status date: 2026-09-20. Verdicts use: COMPLETE / PARTIAL / STUB / BROKEN / MISSING.

## PART 1 — REPOSITORY INVENTORY

Root includes 53 modules (`settings.gradle.kts:131-186`). Stack: AGP 8.5.0, Gradle-tooling 8.6, Kotlin 1.9.24, sora-editor 0.23.4 (`gradle/libs.versions.toml`).

| Area | Location | Purpose | Status |
|---|---|---|---|
| App entry + UI | `core/app/.../app/IDEApplication.kt`, `activities/MainActivity.kt`, `activities/editor/`, `fragments/` (~229 Kt in `core/app`) | App init, project open, editor host, AI chat launcher | Working IDE foundation |
| AI chat (HMX-written) | `core/app/.../ai/` + `activities/aichat/` (~35 files, ~4344 lines AI+knowledge) | Multi-provider chat, summary prompt, `[[WRITE:]]` apply | Functional chat, NOT agent |
| Project intelligence | `core/app/.../knowledge/` + `core/knowledge-api/` + `core/indexing-api/` | Regex + tree-sitter Java parse, FQN index, incremental reindex | Partial |
| Gradle tooling / sync | `tooling/{api,impl,model,events,plugin}` + `services/builder/GradleBuildService.kt`, `ToolingServerRunner.kt` | On-device sync/index via forked tooling server (JSON-RPC) | Working — do not rewrite |
| Remote build (new) | `core/app/.../build/` (RemoteBuildManager, GitHubBuildClient, ProjectGitInfo, GitHubTokenStorage, BuildTools, BuildState) | GitHub Actions dispatch/poll/artifact | Implemented, remote-only |
| Java language | `java/lsp/` (134 files), `java/javac-services/` | Completion, definition, diagnostics, rewrite | Real |
| XML + resources | `xml/{lsp,dom,utils,resources-api,aaptcompiler}` (~195 files) | XML LSP, Manifest perms, AAPT2-derived resource compiler | Strongest area |
| Editor | `editor/{api,impl,lexers,treesitter}` (sora + tree-sitter java/kotlin/xml/json/log) | Editing, highlighting | Working |
| Projects | `core/projects/` (ProjectManagerImpl, WorkspaceImpl, AndroidModule, classpath readers) | Project model, Jar/Zip classpath | Working |
| UI designer | `utilities/uidesigner/`, `utilities/xml-inflater/` | View inflate, visual attrs | Partial-complete |
| Prefs (dual) | `utilities/preferences/` + `AIModelsPreferences.kt` (legacy plaintext) + `ai/storage/ProviderStorage.kt` (encrypted) | Settings incl. AI keys | Fragile: migration incomplete |
| Event bus | `event/{eventbus,eventbus-android,eventbus-events}` | Document events driving context + indexer | Working |
| Logging | `logging/*`, `services/log/` | Patched logback-android, log sender | Working |
| Templates | `utilities/templates-{api,impl}` | Project templates | Working |
| Testing | `testing/*`, `core/app/src/test/.../ai/AiEngineTest.kt` | JUnit/Truth/Robolectric/Barista; AI test covers ChatEngine+ModelManager only | Partial |
| Build logic / vendored | `composite-builds/build-logic/`, `build-deps/*` (jdk-compiler, jdt, javac, ...), `external/logback-android` | Patched on-device compiler deps | Vendored — do not touch |
| Git | JGit 6.8.0, used only in `build/ProjectGitInfo.kt` | detect/commit/push for remote build | Partial |
| Device/ADB | No ADB client. Only `activities/editor/IDELogcatReader.kt` (IDE self-logs) | — | Missing |
| Kotlin LSP | None (`README.md:31` unchecked) | — | Missing |
| NDK/CMake | No handling found | — | Missing |

HMX-written: `ai/`, `knowledge/`, `aichat/`, `build/` (remote), `ContextCache/PromptBuilder`. Inherited AndroidIDE: editor, java/lsp, xml, tooling, projects, uidesigner. Third-party: composite-builds, sora, JGit, Retrofit, Markwon. Bundled JDK17/AAPT2 resolved at runtime on device — NOT DETERMINED from repo alone.

## PART 2 — ARCHITECTURE (actual)

```
IDEApplication -> MainActivity/MainFragment (ProjectManagerImpl/WorkspaceImpl)
 -> EditorActivity (sora + lexers) -> AIChatAction -> AIChatActivity (separate Activity)
 -> GradleBuildService (Service + JSON-RPC to forked tooling/impl child process)
 -> AI layer: AiFactory -> AiEngine -> ProviderRegistry (12 providers)
    AIChatActivity -> ChatEngine -> ContextPipeline -> ContextCache/KnowledgeEngineImpl/MemoryService
 -> KnowledgeEngineImpl: ProjectScanner -> ProjectAnalyzer -> UnifiedIndex(SymbolIndex LRU5000)
 -> RemoteBuild: RemoteBuildManager -> api.github.com; ProjectGitInfo (JGit); BuildTools facade (unwired to AI)
```

No DI framework (Lookup locator + object singletons). Threads: IO for scan/net, Main-scope in Activity (leak-prone, no lifecycleScope), SupervisorJob+IO indexer, CompletableFuture at tooling boundary. AI has zero connection to GradleBuildService channel.

## PART 3 — AI FORENSICS

Entry `AIChatAction.kt` -> `AIChatActivity.kt:42`. Factory `AiFactory.kt`. Interface `AiProvider.kt:13`.

| Capability | Verdict | Evidence |
|---|---|---|
| Provider abstraction | COMPLETE | `AiProvider` + `ProviderRegistry` + `ProviderFactory.createAll` (10 OpenAI-compat + Gemini + Claude) |
| Chat | COMPLETE | `OpenAi/Gemini/ClaudeProvider.chat` via `AiHttpClient.execute` |
| Streaming | PARTIAL/BROKEN | OpenAI SSE parses `optJSONObject("choices")` but API returns JSONArray (empty); Gemini/Claude fake single-chunk flow |
| Tool/function calling | MISSING | `Capability.tools` enum exists, never set; no schema/dispatch; `BuildTools` docstring claims 12 tools, none wired |
| File edit | PARTIAL (fragile) | `[[WRITE:path]]...[[END]]` regex (`AIChatActivity:268`) + Apply-all button, canonical-path guard, no diff/rollback |
| Scan / analysis | COMPLETE (shallow) / PARTIAL | `ProjectScanner` buckets; `ProjectAnalyzer` regex heuristics; no Gradle model/variants/graph |
| Symbol index | PARTIAL | Java tree-sitter + Kotlin regex fallback; FQN->location LRU5000; no refs/callers |
| Context mgmt | PARTIAL | Open tabs/current file tracked; `selectedCode/cursorLine` never populated; keyword router only |
| Prompt | COMPLETE (shallow) | Summary + 10 rules + WRITE syntax; current file injected unbounded (`readText`, no truncation) |
| Memory | Storage COMPLETE / use PARTIAL | `.hmx/memory.db` schema full; pipeline injects <=5 snippets; chat never calls `saveConversation`/sessions |
| Session | PARTIAL | 50-msg in-memory; `openSession/closeSession` never called from chat |
| Model routing | PARTIAL | `ModelManager` cache ok; config dialog is raw text input, not picker |
| Key storage | COMPLETE (new) / BROKEN (migration) | `ProviderStorage` encrypted correct; legacy plaintext `AIModelsPreferences` still readable |
| Errors/retries | PARTIAL | Sealed `AiException` + 401/403/429 map; transport-only 3x retry (429 no backoff) |
| Cancellation | PARTIAL | Stream checks `isActive`; chat not cancellable (button disable only) |
| Token limits/budget/compaction | MISSING | Fields exist, never set; no tokenizer/truncation |
| Log redaction | COMPLETE | Code-only logging, no body/key |

Test `AiEngineTest.kt` covers only history + cache with FakeProvider.

## PART 4 — IS HMX AN AGENT? B (coding assistant, trace of C; not D/E)

Chain today: `USER -> sendMessage -> ChatEngine.send -> provider.chat -> content -> regex collectEdits -> USER presses Apply -> File.writeText`. Missing: tool selection, observation, replan, and every link to Gradle/BuildTools/Logcat/tests/Git-diff/verify. `BuildTools` is dead code from AI perspective.

## PART 5 — CAPABILITY MATRIX

Core: all MISSING except in-memory session (PARTIAL). No AgentLoop/State/Task/Plan/ToolRegistry/Permission/Trace/Replay/Checkpoint classes. Tools: read/search/symbol/build/test/device/APK all MISSING as model-tools (index exists internally); write PARTIAL (WRITE-tag); git PARTIAL (detect/push only); remote-build object exists unwired. Context: file/structure PARTIAL (unbounded/counts); symbol/dep/build graphs, error/diff/test context, compression — MISSING. Safety: traversal guard ok (narrow); approvals/protected-files/command policy — MISSING (AI cannot run commands at all).

## PART 6 — PROJECT UNDERSTANDING: Level 2 (Java-only) / Level 1 for Kotlin

Filename + regex imports/package/class; Java tree-sitter decls; Kotlin regex-only; Manifest regex (fragments always empty STUB); Gradle read as raw text; modules via `/src` segment heuristic (wrong for `core/app`); resources/navigation/variants/flavors/source-sets unparsed. NOT L3+.

## PART 7 — LANGUAGE SUPPORT

Java: highlight/completion/definition/diagnostics/refactor-partial/LSP real (72/100 equivalent). Kotlin: highlight only (25). XML: LSP + resource completion + AAPT2-derived compiler (80). Compose: import-substring detect only, no preview/completion (15). Compile != IDE intelligence — Gradle builds Kotlin/Compose; IDE does not understand them.

## PART 8 — BUILD SYSTEM

On-device sync/index REAL (`GradleBuildService` + `ToolingServerRunner`, init-script + `android.aapt2FromMavenOverride`). Local APK/AAB assemble: NOT FOUND in AI-reachable code — `RemoteBuildManager` header: "NO local APK compilation fallback". No D8/R8/signing/NDK/CMake wiring. Verdicts: Java/Kotlin/Compose/View/multi-module/Maven-deps/KTS builds PARTIAL via remote Actions (med conf); flavors/Gradle-version range NOT DETERMINED; native/NDK MISSING (high conf).

## PART 9 — DEVICE/DEBUG: MISSING

No ADB/install/launch/screenshot/UI-hierarchy/device-cmd/test/crash bridge. `IDELogcatReader` reads IDE self-logs only. Log-sender infra exists (build-log forwarding, not debugging).

## PART 10 — CLINE (public arch; concepts only, Apache-2.0)

Adapt: agent loop+Task state, Tool registry interface, per-tool ask approvals, Git-shadow checkpoints, condense/budget, task-history replay. Keep HMX `AiProvider`. Do NOT use: MCP/multi-agent yet (process/RAM cost). Copy interfaces, never TS files.

## PART 11 — OPENCODE (public arch; concepts only)

Adapt: runtime event-loop idea (as minimal `AgentEvent` flow), permission wrapper design, trace schema (onto SQLite), retry budgets + compaction, cost/fallback fields on ModelManager. Do NOT use: Node runtime, server/SDK split.

## PART 12 — OPENHANDS (public arch, MIT; concepts only)

Copy: Action/Observation event pair (best fit — loop+trace+replay in one type). Adapt: workspace-root guard, session lifecycle, tool executor interface. Do NOT use: multi-agent, MCP yet.

## PART 13 — THREE-WAY

Loop: OpenHands A/O wins (smallest, SQLite-mappable). Tools: Cline registry + OpenHands Action type. Permissions: Cline per-tool ask (small-screen friendly). Checkpoints: Cline shadow-commit via existing JGit. Context: Cline/OpenCode condense reimplemented. Routing: keep HMX's + fallback fields. Rank mechanisms, never whole projects.

## PART 14 — COPY / LATER / NEVER

Now (concept-only): A/O events, tool registry, per-tool approval, shadow commits, condense+budget, retry budget + error extraction. Later: replay UI, cost routing, skill bundles, diff viewer, evals. Never: MCP processes, multi-agent, Node/TS, server split, marketplace, shell/docker, non-Android skills (RAM/battery/scope).

## PART 15 — TARGET ARCHITECTURE

```
IDE (Activities+Editor+GradleBuildService)
 -> HMX Agent Runtime (NEW, pure Kotlin): Loop + Planner + State/Trace + Context Engine
    + Memory + Tool Registry + Permissions + Skills + Model Router + Verification
 -> Android Tool Layer (thin wrappers over EXISTING index/Gradle/JGit; new ADB later)
 -> User project (Workspace-root guarded)
```

MCP deferred; Verification first-class (build-error parser load-bearing).

## PART 16 — MINIMUM TOOL SET

Create: `read_file` (windowed), `search`, `find_symbol` (expose index), typed `inspect_project/module/manifest/gradle/dependencies/resources/variants` (backed by Analyzer v2 + Gradle model). Replace WRITE-tag with diff-patch + approval + rollback. Wire `gradle_sync/build/test`, `build_apk/aab`, `analyze_build_error` to `GradleBuildService` (+remote fallback). Add read-only git status/diff first, then guarded commit/restore. Device tools Phase 6 (new ADB client).

## PART 17 — AGENT LOOP

REQUEST -> INTENT -> INSPECTION (budgeted) -> PLAN -> TOOL (permission-checked, one/turn) -> OBSERVATION (trace) -> ... -> BUILD (local first) -> ERROR ANALYSIS -> FIX -> TEST -> RUNTIME VERIFY -> FINAL (diff + build/test report). Stop: goal met / max 15 (cap 30) / cancel / unrecoverable / denied. Permission on: outside-root writes, delete/move, push, device install, release, secret-adjacent files. Rollback: pre-edit shadow commit. Compaction at 70% window (keep diffs+errors verbatim).

## PART 18 — CONTEXT ENGINE

Budget e.g. sys 2k + summary 1k + files 12k + symbols 3k + errors/diff 6k + history 4k. Order: cursor window (±150, needs plumbing `cursorLine/selectedCode`) -> imported symbols -> same-module -> manifest/gradle-declared -> errors/diff -> memory/prior actions. Truncate largest first; key cache on mtimes.

## PART 19 — MEMORY

Wire existing schema: persist session trace, curated project facts, user rules, task plan/attempts, error-signature->fix. Never persist: file bodies, raw logcat, keys, artifacts. Enforce TTL + existing prune/VACUUM; call `saveConversation` (currently dead) + `cleanupMemory()` at loop end.

## PART 20 — SKILLS: hybrid (prompt file + Kotlin tool-bundle)

Start: kotlin, gradle-build, compose-xml, debug-logcat, room, retrofit, navigation, workmanager, testing, release-signing. Defer firebase/perf/security/a11y to Phase 8.

## PART 21 — SECURITY

P1: dual key stores (plaintext legacy + encrypted) — finish migration, wipe legacy. P1: model-chosen paths (guard ok) w/o blocklist/review — add allowlist+per-file approve. P2: `commitAndPush` stages `.` incl. secrets — require diff+approve + secret scan. P2: raw String tokens in memory (note limitation). P2: no systematic secret redactor. No hardcoded `sk-/ghp_` found; values redacted per instructions.

## PART 22 — QUALITY

Duplication: package/import regex x3, staleness keys x2, Gemini/Claude stream boilerplate. Dead: selection/cursor fields, `fragments`, `BuildTools` claims, orphaned `AIModelsActivity`. Fragile: SSE parse, Kotlin/Manifest/Gradle regex, `/src` module heuristic, unbounded reads, Main-scope + `runBlocking`, god `AIChatActivity/ContextCache/KnowledgeEngineImpl`.

## PART 23 — PERF (low/mid devices)

Keep LRU5000 + 800ms debounce + IO dispatch. Cap: 8k chars/file, 60k retrieval total; 1 model stream + 1 Gradle op at a time; reuse daemon (never fork/turn); backoff GitHub poll (10s x180) + cancel; cap `.hmx/memory.db` + prune.

## PART 24 — SCORECARD (overall 41/100 — Prototype, pre-agent)

IDE 78, Java 72, Kotlin 25, Compose 15, XML 80, Build 55, Understanding 35, AI-arch 50, Agent 12, Context 38, Memory 55, Tools 18, Debug 10, Testing 30, Device 5, Git 40, Security 55, Perf 60, Extensibility 35, Prod-readiness (as IDE) 62.

## PART 25 — GAPS

P0: loop/state/trace; model tools; AI-Gradle wiring; budget/truncation; key-migration + git safety. P1: Kotlin depth, ref graph, Gradle-backed inspect, persistence+compaction, approvals, test-run. P2: ADB/logcat, Compose, flavors, graphs, fallback/cost, replay, skills. P3: MCP, multi-agent, preview-verify, evals.

## PART 26 — PRODUCT DEFINITION

«Android-native autonomous coding environment: understand project, multi-file edit, build, diagnose errors, test, drive device, analyze runtime, self-correct.» Realistic scoped: single-agent + local sync/small builds + remote heavy-APK fallback. NOT realistic P1 on low-end: on-device AAB signing + NDK + huge multi-module + multi-agent + MCP. Termux out of core per brief.

## PART 27 — ROADMAP

0 Cleanup+security (migration, SSE fix, bounds, blocklist). 1 Runtime (events, loop, trace, perms, budgets). 2 Tools (read/search/inspect + diff-patch + shadow rollback). 3 Context (cursor plumbing, ref index, Gradle-backed inspect). 4 Build->fix loop. 5 Testing. 6 ADB/device. 7 Visual verify. 8 Skills (+deferred MCP). 9 Memory+. 10 Hardening.

## PART 28 — DO NOT IMPLEMENT YET (decide first)

State model, tool/result/error schema, permission tiers + protected paths + UX, context budget + truncation order, Action/Observation persistence + retention, model-window/cost/fallback deltas, local-vs-remote build chooser + task allowlist, turn executor + cancel propagation.

## PART 29 — EXECUTIVE SUMMARY

CURRENT: real on-device IDE + multi-provider chat assistant with summary + WRITE-tag drafts. NOT an agent harness. WELL: provider abstraction + encrypted store; scan/summary; Java decl index + reindex; memory schema + `.hmx/`; JGit push + Actions client; sync service + AAPT2 resource logic. LACKS: loop/state/trace, model tools, build/test/device wiring, budgets, chat persistence, Kotlin/Compose depth, NDK/ADB/MCP/skills. BROKEN: SSE parse, faked streams, dual stores, unbounded reads, Main-scope/`runBlocking`, empty fragments, module heuristic, Apply-all UX. TAKE: Cline registry/ask/checkpoint/condense; OpenCode events/retry/failure-log pattern; OpenHands A/O pair + workspace guard. NOT TAKE: MCP/multi-agent/Node/server/marketplace/desktop toolchains. TARGET: PART 15 runtime over typed Android tools. 10 GAPS: loop, tools, Gradle wiring, budget, trace, approvals, persistence, Kotlin/refs, ADB/logcat, key+git safety. PHASE 1 ONLY: runtime foundation + A/O types + 3 read-only tools + permission tiers + PART-28 decisions. Later phases withheld per brief.

Close-out: Score 41/100. Maturity Prototype. Reusable top-10: A/O events, tool registry, per-tool ask, shadow commits, condense/budget, retry budgets, trace store, workspace guard, session lifecycle, cost-aware fallback (all reimplemented Kotlin, zero vendored files). Target PART 15. Phase 1 PART 27-Phase-1.
