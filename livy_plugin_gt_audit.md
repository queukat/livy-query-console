# Livy Query Console Audit for UK Global Talent Framing

Date: 2026-03-29  
Repo: `C:\Users\User\IdeaProjects\livy_new`  
Plugin: `Livy Query Console`  
Marketplace ID: `29406`

## Scope
- This audit evaluates the repository on three separate axes:
- Product / user value
- Technical quality / production maturity
- Evidence value for a UK Global Talent digital technology case
- Final conclusions are based primarily on code and plugin metadata.
- If something is not visible in the repository, it is marked explicitly.

## Downloads / Users
- The `README` contains a JetBrains Marketplace downloads badge at [README.md](/C:/Users/User/IdeaProjects/livy_new/README.md#L7).
- Current badge value checked on 2026-03-29: `125` downloads.
- Important: this is downloads, not verified unique users.
- External sources checked:
- [Marketplace page](https://plugins.jetbrains.com/plugin/29406-livy-query-console)
- [Downloads badge](https://img.shields.io/jetbrains/plugin/d/29406)

## Executive Summary
- This is a real JetBrains plugin with a coherent product kernel, not just a code sample. It solves a narrow but legitimate workflow: running Spark, PySpark, and Spark SQL snippets on a remote cluster via Apache Livy without leaving the IDE. See [README.md](/C:/Users/User/IdeaProjects/livy_new/README.md#L13) and [plugin.xml](/C:/Users/User/IdeaProjects/livy_new/src/main/resources/META-INF/plugin.xml#L17).
- The strongest product value is reduced context switching for Livy-backed exploratory work and day-to-day data engineering/debugging. The user can open a dedicated console, execute selected code or full content, inspect result tabs, then inspect session logs/statements. See [RunCodeViaLivyAction.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/RunCodeViaLivyAction.kt#L22), [LivyConsolePanel.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/editor/ui/LivyConsolePanel.kt#L128), [SessionLogsDialog.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/SessionLogsDialog.kt#L85), and [ShowStatementsDialog.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/ShowStatementsDialog.kt#L48).
- The plugin is technically small, but it is not trivial. It includes custom editor plumbing, persistent settings, an HTTP client, session lifecycle logic, a tool window, release automation, screenshot generation, and targeted tests. See [build.gradle.kts](/C:/Users/User/IdeaProjects/livy_new/build.gradle.kts#L54), [LivyPluginSettings.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/LivyPluginSettings.kt#L10), [LivyClient.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/LivyClient.kt#L15), and [ci.yml](/C:/Users/User/IdeaProjects/livy_new/.github/workflows/ci.yml#L13).
- The strongest technical/product decision is safe managed-session reuse. The code explicitly restricts reuse and cleanup to plugin-managed sessions with matching server and configuration fingerprint, which is a good example of product thinking applied to operational safety. See [SessionManager.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/SessionManager.kt#L35), [LivyManagedSessions.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/LivyManagedSessions.kt#L7), and [LivySessionSpec.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/LivySessionSpec.kt#L54).
- The biggest functional risk is that console follow-up actions do not bind themselves to the server originally used by that console. `cancelStatement()` and `showLogs()` re-create the client from current global settings, so if the URL changes after a run, those actions can target the wrong Livy server. See [LivyPluginSettings.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/LivyPluginSettings.kt#L10), [LivyConsolePanel.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/editor/ui/LivyConsolePanel.kt#L147), [LivyConsolePanel.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/editor/ui/LivyConsolePanel.kt#L198), and [LivyConsolePanel.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/editor/ui/LivyConsolePanel.kt#L223).
- The plugin is usable for a narrow workflow, but it is not yet polished marketplace-grade for broader enterprise use. Not found in the repository: auth model, secure credential storage, connection profiles, persistent console history, richer IDE language integration, or deep statement drill-down.
- For a Global Talent case, this repository is good evidence of a shipped developer tool with product intent, implementation depth, packaging, and release discipline. It is weaker evidence of advanced IntelliJ Platform internals expertise because there is no PSI, inspections, annotators, completion, refactoring, or indexing work registered in `plugin.xml`. See [plugin.xml](/C:/Users/User/IdeaProjects/livy_new/src/main/resources/META-INF/plugin.xml#L94).

### Verdicts
- Product / user value verdict: narrow but real developer-tool value; coherent and repeatable workflow, not a gimmick.
- Technical maturity verdict: solid small plugin with working product surface and release wiring, but not yet fully hardened.
- GT evidence verdict: strong evidence of shipping a real developer tool; usable with framing for JetBrains plugin expertise; weak for advanced IntelliJ internals; unsupported for impact/adoption without external proof.

## What This Plugin Is
- Exact product identity from metadata: `Livy Query Console`, a JetBrains plugin for running Spark, PySpark, and Spark SQL code against Apache Livy from the IDE. See [README.md](/C:/Users/User/IdeaProjects/livy_new/README.md#L1) and [plugin.xml](/C:/Users/User/IdeaProjects/livy_new/src/main/resources/META-INF/plugin.xml#L2).
- Primary use case reconstructed from code:
- Open a dedicated virtual console file
- Execute selected text or full console text via Livy
- Wait for statement completion
- Review output in raw / pretty / table form
- Inspect sessions, logs, and recent statements if needed
- Relevant code path: [RunCodeViaLivyAction.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/RunCodeViaLivyAction.kt#L29), [LivyConsoleFileEditor.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/editor/LivyConsoleFileEditor.kt#L16), and [LivyConsolePanel.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/editor/ui/LivyConsolePanel.kt#L128).
- Intended user, visible from product surface:
- data engineer
- analytics engineer
- backend engineer
- potentially QA/support/devops staff working with Spark/Livy environments
- This inference is supported by the product language in [plugin.xml](/C:/Users/User/IdeaProjects/livy_new/src/main/resources/META-INF/plugin.xml#L12).
- Secondary use cases:
- connection diagnostics
- managed test session creation
- session browsing across the current server
- log inspection and troubleshooting
- See [LivyPluginConfigurable.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/LivyPluginConfigurable.kt#L116) and [LivySessionsPanel.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/bottompanel/LivySessionsPanel.kt#L75).
- Not found in repository:
- notebook workflow
- job submission history
- saved snippets
- multi-profile connection management
- deep semantic IDE assistance
- attach-to-existing-session chooser as a first-class flow

## Product / Business Value Review
### What value it creates
- It saves context switches. A user can stay in JetBrains IDE instead of moving to curl, Postman, a browser UI, or separate shell tooling. See [plugin.xml](/C:/Users/User/IdeaProjects/livy_new/src/main/resources/META-INF/plugin.xml#L12).
- It saves routine friction in repeated debugging/exploration loops. `Run` reuses or creates managed sessions, displays output, and exposes logs/statements in adjacent UI. See [SessionManager.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/SessionManager.kt#L29) and [LivyConsolePanel.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/editor/ui/LivyConsolePanel.kt#L231).
- It reduces operational mistakes versus naive session reuse. The plugin does not treat arbitrary Livy sessions as safe to reuse or auto-delete. See [SessionManager.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/SessionManager.kt#L52).

### How real the problem is
- The use case is narrow, but real. For teams using Apache Livy, remote interactive execution is a known workflow. The plugin addresses a painful, repeatable loop rather than a one-off novelty.
- This is not a broad “data platform” product. It is a focused remote snippet runner and diagnostics tool. That is acceptable product scope.

### Frequency of use
- Likely regular or daily for the right user segment.
- Low value for users who do not work with Livy-backed Spark sessions.
- Medium to high value for a user who repeatedly tests transformations, SQL snippets, or runtime environment behavior.

### Strongest product differentiator
- Safe managed-session policy:
- same server only
- matching fingerprint only
- bounded managed-session limit
- optional deletion only of oldest idle managed session
- See [LivyPluginConfigurable.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/LivyPluginConfigurable.kt#L104), [LivyManagedSessions.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/LivyManagedSessions.kt#L64), and [SessionManager.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/SessionManager.kt#L85).

### Where value is weaker
- The console is plain text only. No syntax support, no language-aware completion, no code intelligence. See [LivyConsolePanel.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/editor/ui/LivyConsolePanel.kt#L47).  
  Class: `important next step`
- Settings are app-global, which weakens multi-environment use. See [LivyPluginSettings.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/LivyPluginSettings.kt#L10).  
  Class: `core gap for current use case` when users switch servers.
- Statements browser is shallow. It shows a list and output status, but not a proper statement details view. See [ShowStatementsDialog.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/ShowStatementsDialog.kt#L22).  
  Class: `important next step`
- Not found in repository: authentication story. In secured environments this may be a direct blocker.  
  Class: `core gap for current use case` in enterprise/protected Livy deployments.

## What Is Actually Implemented
### Declared extension points
- `applicationConfigurable`: [plugin.xml](/C:/Users/User/IdeaProjects/livy_new/src/main/resources/META-INF/plugin.xml#L96)
- `toolWindow`: [plugin.xml](/C:/Users/User/IdeaProjects/livy_new/src/main/resources/META-INF/plugin.xml#L103)
- `fileType`: [plugin.xml](/C:/Users/User/IdeaProjects/livy_new/src/main/resources/META-INF/plugin.xml#L110)
- `fileEditorProvider`: [plugin.xml](/C:/Users/User/IdeaProjects/livy_new/src/main/resources/META-INF/plugin.xml#L116)
- actions group and actions: [plugin.xml](/C:/Users/User/IdeaProjects/livy_new/src/main/resources/META-INF/plugin.xml#L119)

### Implemented user flows
- Open console from Tools menu: [plugin.xml](/C:/Users/User/IdeaProjects/livy_new/src/main/resources/META-INF/plugin.xml#L127)
- Open console from editor popup with selected text: [plugin.xml](/C:/Users/User/IdeaProjects/livy_new/src/main/resources/META-INF/plugin.xml#L139)
- Open sessions tool window: [plugin.xml](/C:/Users/User/IdeaProjects/livy_new/src/main/resources/META-INF/plugin.xml#L132)
- Configure Livy settings: [LivyPluginConfigurable.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/LivyPluginConfigurable.kt#L41)
- Test connection and open verbose diagnostics: [LivyPluginConfigurable.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/LivyPluginConfigurable.kt#L116)
- Start a managed test session: [LivyPluginConfigurable.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/LivyPluginConfigurable.kt#L165)
- Execute code in background and wait for statement completion: [LivyConsolePanel.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/editor/ui/LivyConsolePanel.kt#L143)
- Cancel running statement: [LivyConsolePanel.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/editor/ui/LivyConsolePanel.kt#L198)
- Show logs for last used session: [LivyConsolePanel.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/editor/ui/LivyConsolePanel.kt#L223)
- Refresh sessions table: [LivySessionsPanel.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/bottompanel/LivySessionsPanel.kt#L75)
- Choose visible session columns: [LivySessionsPanel.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/bottompanel/LivySessionsPanel.kt#L105)
- Open statements dialog from sessions: [LivySessionsPanel.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/bottompanel/LivySessionsPanel.kt#L134)
- Open logs dialog from sessions: [LivySessionsPanel.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/bottompanel/LivySessionsPanel.kt#L148)

### What is not implemented
- Not found in repository:
- inspections
- intentions
- annotators
- line markers
- code vision
- listeners/startup activities
- run configurations
- project services
- PSI-based language support
- secure credential storage

## UX / Delivery Review
### Discoverability
- Good:
- clear README quick start in [README.md](/C:/Users/User/IdeaProjects/livy_new/README.md#L58)
- action names are mostly honest
- dedicated settings page under Tools
- Weak:
- no onboarding inside the IDE
- no first-run guidance if configuration is missing
- no console banner showing current server/session context
- no empty-state guidance in sessions panel beyond an empty table

### Usability
- Run/Cancel/Show Logs in the console are simple and understandable. See [LivyConsolePanel.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/editor/ui/LivyConsolePanel.kt#L104).
- The result viewer is practical. Raw / Table / Pretty / Error tabs provide useful progressive disclosure. See [LivyConsolePanel.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/editor/ui/LivyConsolePanel.kt#L273).
- Session logs include search next/prev. See [SessionLogsDialog.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/SessionLogsDialog.kt#L103).
- The sessions tool window is functional but visually utilitarian. It behaves more like an internal utility panel than a polished JetBrains tool window. See screenshot assets in [docs/screenshots/README.md](/C:/Users/User/IdeaProjects/livy_new/docs/screenshots/README.md).

### Friction points
- Global settings can silently change the meaning of follow-up console actions.  
  Class: `core gap for current use case`
- Result tabs accumulate without apparent management controls.  
  Class: `important next step`
- No persistent console history or saved snippets.  
  Class: `important next step`
- Statements table does not open statement details.  
  Class: `important next step`
- Verbose diagnostics are shown in a generic `Execution Result` dialog title, which is slightly misleading. See [ResultDialog.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/ResultDialog.kt#L23).  
  Class: `quick safe fix`

## Architecture Review
### Strengths
- Good separation for a small plugin:
- HTTP API in [LivyClient.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/LivyClient.kt#L15)
- shared client/provider in [LivyClientProvider.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/LivyClientProvider.kt#L12)
- settings persistence in [LivyPluginSettings.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/LivyPluginSettings.kt#L12)
- session policy in [SessionManager.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/SessionManager.kt#L11)
- managed-session registry in [LivyManagedSessions.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/LivyManagedSessions.kt#L3)

### Weaknesses
- `LivyConsolePanel` is doing too much:
- toolbar actions
- execution orchestration
- cancellation state
- result rendering
- ASCII table parsing
- disposal logic
- See [LivyConsolePanel.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/editor/ui/LivyConsolePanel.kt#L29).  
  Class: `important next step`
- `LivyPluginConfigurable` is also overloaded with form binding, validation, diagnostics actions, and managed test session side effects. See [LivyPluginConfigurable.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/LivyPluginConfigurable.kt#L13).  
  Class: `important next step`
- App-level global state limits future extensibility for multiple environments or profiles. See [LivyPluginSettings.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/LivyPluginSettings.kt#L10).  
  Class: `important next step`

## IntelliJ Platform Review
### Correct usage
- `PersistentStateComponent` usage is straightforward and acceptable: [LivyPluginSettings.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/LivyPluginSettings.kt#L12)
- `Task.Backgroundable` is used for network operations: [LivyBackground.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/LivyBackground.kt#L21)
- Actions declare update thread explicitly: [RunCodeViaLivyAction.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/RunCodeViaLivyAction.kt#L15), [OpenLivySessionsToolWindowAction.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/OpenLivySessionsToolWindowAction.kt#L10), and [LivyConsolePanel.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/editor/ui/LivyConsolePanel.kt#L105)
- Tool window creation is simple and correct: [LivySessionsWindowFactory.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/LivySessionsWindowFactory.kt#L12)

### Platform-specific concerns
- The plugin uses `LightVirtualFile` from `com.intellij.testFramework` in production code: [RunCodeViaLivyAction.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/RunCodeViaLivyAction.kt#L10).  
  This is a platform smell, even if it currently works.  
  Class: `important next step`
- The file editor reports `isModified = false` and state is effectively stubbed: [LivyConsoleFileEditor.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/editor/LivyConsoleFileEditor.kt#L34).  
  This matches the current ephemeral console concept but weakens editor semantics.  
  Class: `scale-up / not required now`
- No PSI, index, or dumb-mode-sensitive heavy integrations are present. That keeps risk low, but it also means the plugin is not demonstrating advanced IntelliJ Platform editor intelligence.

## Performance Review
- Positive:
- no broad listeners found
- no PSI/event subscriptions found
- network and polling are off the EDT
- no obvious notification loops or polling daemons
- Risks:
- `getAllSessions()` is called in refresh and session-management paths and walks all pages every time: [LivyClient.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/LivyClient.kt#L129) and [SessionManager.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/SessionManager.kt#L23).  
  On large shared clusters this can become expensive.  
  Class: `important next step`
- Polling uses fixed `Thread.sleep(1000)` loops for session creation and statement completion: [SessionManager.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/SessionManager.kt#L121) and [LivyConsolePanel.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/editor/ui/LivyConsolePanel.kt#L351).  
  This is acceptable for the current scope.  
  Class: `scale-up / not required now`
- Logs and statements are hard-capped at `5000` log lines and `50` statements: [SessionLogsDialog.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/SessionLogsDialog.kt#L89) and [ShowStatementsDialog.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/ShowStatementsDialog.kt#L52).  
  Safe for latency, but incomplete for deep investigation.  
  Class: `important next step`

## Reliability / Supportability Review
- Good:
- execution is cancellable
- waiting is bounded with timeout
- console dispose cancels active work
- managed sessions are pruned and cleaned carefully
- See [LivyConsolePanel.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/editor/ui/LivyConsolePanel.kt#L336), [LivyConsolePanel.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/editor/ui/LivyConsolePanel.kt#L414), and [SessionManager.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/SessionManager.kt#L128).
- Weak:
- logging is very limited; most failures go only to modal dialogs, not to structured logs. See [LivyClient.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/LivyClient.kt#L20).  
  Class: `important next step`
- local packaging reliability is imperfect. On 2026-03-29, `./gradlew.bat test` and `./gradlew.bat verifyPlugin` succeeded locally, but `./gradlew.bat buildPlugin` failed reproducibly on Windows in `:buildSearchableOptions` with a mapped file error inside JetBrains searchable options generation.  
  Class: `important next step`
- CI does not currently run `verifyPlugin` or `runPluginVerifier`, and release does not re-run tests on tag builds. See [ci.yml](/C:/Users/User/IdeaProjects/livy_new/.github/workflows/ci.yml#L38) and [release.yml](/C:/Users/User/IdeaProjects/livy_new/.github/workflows/release.yml#L37).  
  Class: `important next step`

## Security / Privacy Review
- The product necessarily sends code to the configured Livy endpoint. This is explicit behavior, not hidden behavior. See [LivyClient.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/LivyClient.kt#L98).
- Not found in repository:
- token storage
- password storage
- PasswordSafe integration
- auth interceptors
- cookie/session auth model
- OAuth/Kerberos/SPNEGO support
- This means there is no obvious secret leakage implementation, but it also means the plugin likely does not cover many secured real-world deployments.
- SSL handling is delegated to JetBrains Runtime trust configuration, documented in [README.md](/C:/Users/User/IdeaProjects/livy_new/README.md#L96).
- Verbose connection diagnostics can display response headers/body preview to the user. This is useful for troubleshooting, but may expose server details in the UI. See [LivyClient.kt](/C:/Users/User/IdeaProjects/livy_new/src/main/kotlin/com/queukat/livy_new/LivyClient.kt#L247).

## Testing Review
- Present:
- targeted logic tests for managed sessions: [LivyManagedSessionsTest.kt](/C:/Users/User/IdeaProjects/livy_new/src/test/kotlin/com/queukat/livy_new/LivyManagedSessionsTest.kt#L6)
- targeted logic tests for session spec defaults/fingerprint behavior: [LivySessionSpecFactoryTest.kt](/C:/Users/User/IdeaProjects/livy_new/src/test/kotlin/com/queukat/livy_new/LivySessionSpecFactoryTest.kt#L7)
- screenshot-generation tests for visible UI states: [LivyUiScreenshotTest.kt](/C:/Users/User/IdeaProjects/livy_new/src/test/kotlin/com/queukat/livy_new/LivyUiScreenshotTest.kt#L24)
- Missing:
- `SessionManager` orchestration tests
- fake-client tests for reuse/create/delete flow
- console run/cancel integration tests
- wrong-server regression test
- tool window action smoke tests
- error recovery and timeout edge cases
- Plugin Verifier reports are configured, but no generated report artifacts are present in the repository.

## Stated vs Actual Value
| Claimed | Actual in code | Gap | Class |
|---|---|---|---|
| Run Spark / PySpark / SQL through Livy from IDE | Confirmed in code and metadata | None at core use-case level | `confirmed` |
| Result viewer with raw / pretty / table output | Confirmed | Table parsing is ASCII-table based only | `important next step` |
| Session logs and statement browser | Confirmed | Statements browser is shallow | `important next step` |
| Safer session management | Confirmed strongly | Console/server binding still unsafe after settings change | `core gap for current use case` |
| Marketplace-ready plugin | Partially confirmed | Local Windows `buildPlugin` packaging issue observed | `important next step` |
| Open-source MIT | README claims MIT | `LICENSE` file not found in repo | `quick safe fix` |

## GT Evidence Review
### What the repository proves strongly
- The author shipped a real developer tool, not just a code demo.
- Evidence:
- product metadata and positioning in [plugin.xml](/C:/Users/User/IdeaProjects/livy_new/src/main/resources/META-INF/plugin.xml#L6)
- complete end-to-end user flow in [README.md](/C:/Users/User/IdeaProjects/livy_new/README.md#L58)
- concrete UI surfaces and execution code
- CI/release/publish setup in [.github/workflows/ci.yml](/C:/Users/User/IdeaProjects/livy_new/.github/workflows/ci.yml#L1), [.github/workflows/release.yml](/C:/Users/User/IdeaProjects/livy_new/.github/workflows/release.yml#L1), and [.github/workflows/publish-marketplace.yml](/C:/Users/User/IdeaProjects/livy_new/.github/workflows/publish-marketplace.yml#L1)
- screenshot generation and documentation in [build.gradle.kts](/C:/Users/User/IdeaProjects/livy_new/build.gradle.kts#L54) and [docs/screenshots/README.md](/C:/Users/User/IdeaProjects/livy_new/docs/screenshots/README.md)

### What it proves with good framing
- Product thinking:
- safe session ownership boundaries
- explicit managed-session semantics in settings copy
- connection diagnostics for real-world failures
- honest correction of misleading UI surface documented in [docs/fix_plan.md](/C:/Users/User/IdeaProjects/livy_new/docs/fix_plan.md#L68)
- JetBrains plugin delivery capability:
- custom editor surface
- tool window
- persistent settings
- application services
- plugin.xml metadata

### What it proves weakly
- Deep IntelliJ Platform expertise in PSI/editor intelligence
- There is little evidence of:
- PSI manipulation
- indexing-aware functionality
- code inspections
- semantic completion
- refactoring support
- custom language parsing/highlighting

### Unsupported from code alone
- number of real users
- retention
- productivity impact
- commercial or organizational adoption
- public recognition
- recommendations from users or peers
- installs beyond the current download badge count

## Evidence Matrix
| Claim | Repo proof | Strength | External proof to add |
|---|---|---|---|
| Built a real developer productivity tool | End-to-end execution, sessions, logs, settings | Strong evidence | Marketplace page, demo, screenshots, user walkthrough |
| Understands operational safety and lifecycle | Managed-session policy and cancellable background tasks | Strong evidence | User testimonials, internal production usage |
| Can package and release JetBrains plugins | CI/release/publish wiring | Usable with framing | Release history, signed builds, Marketplace analytics |
| Has advanced IntelliJ Platform internals expertise | Basic plugin integration only | Weak evidence | Separate projects, talks, advanced code samples |
| Tool has market impact | README badge only shows downloads | Unsupported from code alone | Install stats, reviews, internal adoption, recommendation letters |

## Business / User Strengths
- Real repetitive workflow solved
- Good coherence around one primary task
- Safe managed-session model
- Useful diagnostics surface for logs/statements
- Marketplace packaging and screenshots improve presentation

## Business / User Gaps
- Single global connection model weakens everyday multi-environment use
- No auth story visible
- No saved history/profile model
- No deeper statement inspection
- Narrow audience by design

## Technical Strengths
- Clear small-plugin architecture
- Correct use of background tasks for network work
- Sensible persistent state model
- Reuse/delete safety stronger than many small tools
- Basic tests and CI exist

## Technical Risks
- Wrong-server follow-up actions from global settings
- `LightVirtualFile` from testFramework in production path
- Weak logging and observability
- Incomplete packaging reliability on local Windows
- Test coverage does not yet protect orchestration-heavy paths

## Blockers
- Wrong-server bug for console follow-up actions.  
  Class: `core gap for current use case`
- Missing auth support for secured Livy environments.  
  Class: `core gap for current use case` in many real deployments

## Quick Safe Fixes
- Add `LICENSE` file to match README claim.
- Rename verbose diagnostics dialog title from `Execution Result` to a diagnostics-specific title.
- Show current server URL and session id in the console header after first execution.
- Disable or clarify buttons when no session is in context.

## Recommended Fix Plan
1. Bind each console to a specific `baseUrl` and session context.
- Why: fixes the main correctness issue.
- Business impact: prevents dangerous cross-server confusion.
- Technical impact: makes console behavior deterministic.
- GT evidence impact: shows rigor in product hardening.
- Risk if ignored: cancel/log operations can target the wrong server.

2. Introduce connection profiles and auth-ready abstractions.
- Why: current app-global settings do not scale beyond the simplest use case.
- Business impact: expands real-world usability materially.
- Technical impact: creates a proper foundation for secure deployments.
- GT evidence impact: strengthens product thinking narrative.
- Risk if ignored: plugin remains limited to permissive environments.

3. Add tests around `SessionManager` and console run/cancel flows.
- Why: these are core product behaviors but not well protected.
- Business impact: lower regression risk.
- Technical impact: safer future refactors.
- GT evidence impact: stronger production maturity signal.
- Risk if ignored: safety claims remain under-tested.

4. Improve tool-window and statement inspection UX.
- Why: product value exists, but UI still feels utilitarian.
- Business impact: improves adoption and daily usability.
- Technical impact: low-to-medium complexity.
- GT evidence impact: stronger polish and user empathy signal.
- Risk if ignored: product remains obviously early-stage.

5. Stabilize `buildPlugin` on Windows and add `verifyPlugin` to CI.
- Why: release confidence should not depend on a narrower environment.
- Business impact: smoother maintenance and contribution path.
- Technical impact: better packaging discipline.
- GT evidence impact: stronger shipping discipline story.
- Risk if ignored: operational maturity remains visibly uneven.

## Final Verdict
- This plugin is worth developing further.
- It already has real product value as a niche JetBrains-based developer tool for Livy/Spark workflows.
- The current repository is credible evidence of product creation, engineering ownership, and plugin delivery.
- It is not yet strong evidence of advanced IntelliJ Platform internals specialization.
- For a UK Global Talent case, the best framing is:
- a real shipped developer tool
- solving a genuine workflow problem
- showing product intent, implementation depth, release discipline, and iterative hardening
- This repository should ideally be supplemented with:
- Marketplace stats and screenshots
- user testimonials or recommendation letters
- evidence of internal or external adoption
- release/demo artifacts
- any blog post, talk, or write-up explaining the problem solved and usage context
