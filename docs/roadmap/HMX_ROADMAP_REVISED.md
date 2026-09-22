# HMX ROADMAP — REVISED PROJECT ORDER

## IMPORTANT ARCHITECTURAL DECISION

The project has three separate execution environments:

1. Android IDE / Agent Runtime — the permanent core.
2. Remote Build / Termux / Device tooling — execution backends added progressively.
3. Local Android Build Engine — FINAL phase of the entire project.

The Agent Runtime MUST NOT depend directly on the Local Build Engine.

The Agent Runtime must communicate with builds through an abstract "BuildBackend" / "BuildService" interface.

This allows:

```
HMX Agent
    |
    v
Build Backend Interface
    |
    +---- Remote GitHub Actions Backend     [current]
    |
    +---- Termux Backend                    [later]
    |
    +---- Local Android Build Engine        [FINAL]
```

This prevents the agent architecture from being rewritten when local compilation is eventually introduced.

---

## PART 25 — REVISED PRIORITY GAPS

### P0 — Agent Foundation

- Agent loop
- Agent state
- Action/Observation model
- Tool registry
- Tool execution
- Permission system
- Context budget
- Cancellation
- Trace/event system
- Model-to-tool communication

### P1 — Agent Productivity

- Read/search tools
- Symbol tools
- Project inspection
- Diff/patch editing
- Rollback/checkpoints
- Context retrieval
- Build-tool integration
- Error extraction
- Remote build integration

### P2 — Deep Android Intelligence

- Kotlin project intelligence
- Reference graph
- Gradle model
- Android module model
- Manifest understanding
- Resource understanding
- Build variants
- Flavors
- Compose understanding
- Better dependency graph

### P3 — Runtime Development

- Testing tools
- ADB
- Device management
- Logcat
- APK installation
- App launch
- UI hierarchy
- Screenshot capture
- Runtime error analysis

### P4 — Advanced Agent Platform

- Skills
- MCP
- Replay
- Advanced memory
- Cost/model routing
- Evaluation framework
- Visual verification
- Autonomous multi-step workflows

### P5 — Termux Integration

Termux is NOT part of the initial Android-only core.

It will be added later as an optional execution environment.

Potential future capabilities:

- Termux process bridge
- command execution
- filesystem bridge
- Git/CLI tools
- external developer tools
- optional Linux tooling
- optional scripting environment

Termux must remain a backend/tool provider rather than becoming a dependency of the HMX Agent Runtime.

### P6 — FINAL: LOCAL ANDROID BUILD ENGINE

The Local Android Build Engine is the FINAL major subsystem of the entire project.

Do NOT make local APK/AAB compilation a prerequisite for the agent runtime.

Do NOT redesign the Agent Runtime around local compilation.

The final local build system will eventually handle, as supported:

- JDK
- Gradle
- Android Gradle Plugin
- Kotlin compiler
- Android SDK
- Android Build Tools
- AAPT2
- D8
- R8
- APK packaging
- AAB packaging
- signing
- resource processing
- multi-module projects
- build variants
- product flavors
- NDK
- CMake
- native libraries
- build caching
- toolchain resolution

The implementation of this subsystem must occur only after the agent, tools, project intelligence, remote build workflow, testing, device integration, and Termux integration are mature.

---

## PART 26 — FINAL PRODUCT DEFINITION

HMX is an:

«Android-native autonomous coding environment focused on building Android applications.»

The intended workflow is:

```
USER REQUEST
      |
      v
UNDERSTAND PROJECT
      |
      v
PLAN
      |
      v
INSPECT
      |
      v
EDIT MULTIPLE FILES
      |
      v
DIFF / REVIEW
      |
      v
BUILD BACKEND
      |
      +---- Remote GitHub Actions
      |
      +---- Termux [future]
      |
      +---- Local Build Engine [FINAL]
      |
      v
READ BUILD RESULT
      |
      v
DIAGNOSE
      |
      v
AUTO-FIX
      |
      v
TEST
      |
      v
DEVICE VERIFICATION
      |
      v
RUNTIME ANALYSIS
      |
      v
FINAL VERIFICATION
```

The core AI Agent must remain independent from the execution backend.

---

## PART 27 — COMPLETE PROJECT ROADMAP

### Phase 0 — Cleanup + Security

Objectives:

- Finish encrypted AI-key migration.
- Remove/retire legacy plaintext provider storage.
- Fix OpenAI SSE parsing.
- Remove fake streaming behavior where appropriate.
- Add file-size/context bounds.
- Fix lifecycle/threading problems.
- Fix unsafe Git staging behavior.
- Establish protected paths.
- Establish project-root security.
- Clean dead/orphaned AI components where confirmed safe.

Do not redesign the agent yet.

---

### Phase 1 — Agent Runtime Foundation

Objectives:

Create the core HMX Agent Runtime in pure Kotlin.

Implement:

- Agent
- AgentLoop
- AgentState
- AgentSession
- AgentEvent
- Action
- Observation
- Tool interface
- Tool registry
- Tool executor
- Permission model
- Cancellation
- Context budget primitives
- Agent trace
- iteration limits
- basic failure handling

Initial tools should be READ-ONLY.

The runtime must be independent from:

- Gradle build execution
- GitHub Actions
- Termux
- Local build engine
- ADB
- MCP
- multi-agent systems

Phase 1 establishes the foundation only.

---

### Phase 2 — Agent Tool System

Add:

- read_file
- search
- find_symbol
- inspect_project
- inspect_module
- inspect_manifest
- inspect_gradle
- inspect_dependencies
- inspect_resources

Replace the current "[[WRITE:]]" mechanism with:

```
Agent
  |
  v
Edit proposal
  |
  v
Diff
  |
  v
Permission
  |
  v
Apply
```

Add:

- patch-based editing
- diff generation
- protected files
- workspace-root guard
- rollback/checkpoint foundation

---

### Phase 3 — Deep Project Intelligence

Upgrade:

- Kotlin understanding
- Java references
- symbol relationships
- callers/callees
- module relationships
- Gradle model
- Android module detection
- Manifest model
- resource model
- navigation model
- dependencies
- variants
- flavors
- source sets

Target:

```
Level 2
    ↓
Level 3
    ↓
Level 4
    ↓
Level 5+
```

Do not confuse text scanning with semantic understanding.

---

### Phase 4 — Context + Memory Engine

Implement:

- token/context budgets
- context retrieval
- context ranking
- truncation
- compaction
- session persistence
- task persistence
- agent history
- project facts
- user rules
- error history
- previous action history

The agent should retrieve relevant context instead of sending the entire repository.

---

### Phase 5 — Remote Build → Diagnose → Auto-Fix

Use the EXISTING remote GitHub Actions build system.

Do NOT implement local APK compilation here.

Connect:

```
Agent
 ↓
Build Tool
 ↓
RemoteBuildManager
 ↓
GitHub Actions
 ↓
Build Result
 ↓
Build Logs
 ↓
Error Parser
 ↓
Agent
```

The autonomous loop becomes:

```
EDIT
 ↓
BUILD
 ↓
FAIL
 ↓
ANALYZE
 ↓
FIX
 ↓
BUILD
 ↓
PASS
```

Add retry limits and failure classification.

---

### Phase 6 — Testing

Add agent tools for:

- unit tests
- Android tests
- Compose tests where supported
- test result parsing
- test failure analysis
- automatic correction

Workflow:

```
CODE
 ↓
BUILD
 ↓
TEST
 ↓
FAIL
 ↓
ANALYZE
 ↓
FIX
 ↓
TEST AGAIN
```

---

### Phase 7 — Android Device Agent

Add:

- ADB client
- device discovery
- APK installation
- app launch
- app stop
- Logcat
- crash extraction
- UI hierarchy
- device information

The agent should be able to:

```
BUILD
 ↓
INSTALL
 ↓
LAUNCH
 ↓
READ LOGCAT
 ↓
ANALYZE
 ↓
FIX
```

---

### Phase 8 — Visual / Runtime Verification

Add:

- screenshot capture
- screenshot analysis
- UI hierarchy analysis
- visual regression
- runtime verification
- visual bug diagnosis

Eventually:

```
Screenshot
 ↓
Vision Model
 ↓
UI Problem
 ↓
Source Mapping
 ↓
Code Fix
 ↓
Build
 ↓
Screenshot
 ↓
Compare
```

---

### Phase 9 — Skills + MCP + Advanced Agent Capabilities

Add:

- Android skills
- Kotlin skill
- Gradle skill
- Compose skill
- XML skill
- testing skill
- debugging skill
- release skill
- MCP support
- advanced model routing
- cost tracking
- replay
- evaluation framework

MCP should remain optional and modular.

Do not make MCP a requirement for the core agent.

---

### Phase 10 — Production Hardening

Focus on:

- reliability
- lifecycle safety
- memory usage
- cancellation
- crash recovery
- trace persistence
- security
- secret protection
- performance
- large project handling
- model failure recovery
- network failure recovery
- remote-build failure recovery
- UI/UX for agent approval
- regression tests

---

### Phase 11 — TERMUX INTEGRATION

Termux is introduced ONLY after the Android coding-agent pipeline is mature.

Termux should be implemented as an optional execution backend.

Architecture:

```
HMX Agent
    |
    v
Execution Backend Interface
    |
    +---- Android-native tools
    |
    +---- Remote build
    |
    +---- Termux backend
```

Potential capabilities:

- shell commands
- Git CLI
- external CLI tools
- scripting
- package/tool installation
- project utilities
- advanced developer workflows

Termux must NOT become a hard dependency of HMX.

The HMX Agent Runtime must function without Termux.

---

### Phase 12 — FINAL: LOCAL ANDROID BUILD ENGINE

This is the FINAL major subsystem.

Only begin after all previous phases are stable.

Architecture:

```
HMX Agent
    |
    v
BuildBackend
    |
    +---- RemoteBuildBackend
    |
    +---- TermuxBuildBackend
    |
    +---- LocalBuildBackend
                 |
                 +---- JDK
                 +---- Gradle
                 +---- AGP
                 +---- Kotlin
                 +---- SDK
                 +---- Build Tools
                 +---- AAPT2
                 +---- D8
                 +---- R8
                 +---- NDK
                 +---- CMake
                 +---- Signing
                 +---- Artifact management
```

The Local Build Engine must NOT leak implementation details into the Agent Runtime.

The agent only sees:

```
BuildRequest
BuildProgress
BuildResult
BuildError
Artifact
```

This keeps the architecture replaceable and future-proof.

---

## PART 28 — ARCHITECTURAL DECISIONS NOW LOCKED

The following decisions are now fixed for the roadmap:

1. HMX is Android-first.
2. The Agent Runtime is pure Kotlin.
3. The Agent Runtime is independent of build implementation.
4. Remote GitHub Actions remains the current build backend.
5. Termux is a later optional backend.
6. Local Android compilation is the FINAL major phase.
7. MCP is optional and deferred.
8. Multi-agent architecture is deferred.
9. No Node/TypeScript runtime is required for the core agent.
10. No generic Linux shell architecture belongs in the current core.
11. Existing Gradle Tooling infrastructure should not be rewritten unnecessarily.
12. Existing provider abstraction remains the model layer.
13. Cline/OpenCode/OpenHands code should not be blindly vendored.
14. Their useful mechanisms should be reimplemented in Kotlin.
15. Agent tools communicate through typed interfaces.
16. Build backends communicate through typed interfaces.
17. Workspace boundaries must be enforced.
18. Agent actions must be observable and traceable.
19. Agent edits must be reviewable and reversible.
20. Every autonomous workflow must have cancellation and iteration limits.

---

## PART 29 — PHASE 1 BOUNDARY

Phase 1 MUST NOT implement:

- local APK building
- Termux
- ADB
- MCP
- multi-agent
- visual AI
- remote build execution
- automatic code modification
- autonomous Git push
- device installation

Phase 1 builds the foundation required for those future capabilities.

Phase 1 must be small, testable, deterministic, and reusable.

---

## FINAL ROADMAP

```
PHASE 0
Cleanup + Security
        ↓
PHASE 1
Agent Runtime
        ↓
PHASE 2
Tool System
        ↓
PHASE 3
Project Intelligence
        ↓
PHASE 4
Context + Memory
        ↓
PHASE 5
Remote Build + Auto Fix
        ↓
PHASE 6
Testing
        ↓
PHASE 7
ADB / Device Agent
        ↓
PHASE 8
Visual Verification
        ↓
PHASE 9
Skills + MCP
        ↓
PHASE 10
Production Hardening
        ↓
PHASE 11
Termux Integration
        ↓
PHASE 12
LOCAL ANDROID BUILD ENGINE
        ↓
        FINAL
```

The Local Build Engine is therefore explicitly the last major subsystem, not part of Phase 1–11.
