# User Flow

## Real user flow reconstructed from the repository

### 1. Open a work file or route source into one

- From the Tools menu or context menu, the user can open a reusable Livy work file via `RunCodeViaLivyAction.actionPerformed()`; that action prompts for a connection profile and captures a `LivyExecutionTarget` snapshot before opening the surface (`src/main/kotlin/com/queukat/livy_new/RunCodeViaLivyAction.kt`, `src/main/kotlin/com/queukat/livy_new/LivyExecutionTarget.kt`).
- From the editor/project popup, the user can run the current selection/line or the whole file directly into Livy via `RunSelectionOrLineInLivyAction` and `RunFileInLivyAction`; both also prompt for a profile and capture a `LivyExecutionTarget` snapshot first (`src/main/kotlin/com/queukat/livy_new/RunSelectionOrLineInLivyAction.kt`, `src/main/kotlin/com/queukat/livy_new/RunFileInLivyAction.kt`).
- The work surface is reused per `profileId + work-file mode` if already open; otherwise a new `LightVirtualFile` with extension `.livyconsole` is created (`findOrCreateLivyWorkFile()` in `src/main/kotlin/com/queukat/livy_new/LivyConsoleLauncher.kt`).

### 2. Bind the work file to a profile snapshot, not live mutable settings

- `LivyExecutionTarget.capture()` clones the selected profile, normalizes the base URL, and stores the snapshot alongside the profile id and display name (`src/main/kotlin/com/queukat/livy_new/LivyExecutionTarget.kt`).
- The target is attached to the virtual file through `LivyExecutionTargets.attach()` and later resolved from that attachment, not recomputed on every run (`src/main/kotlin/com/queukat/livy_new/LivyExecutionTarget.kt`).
- The snapshot behavior is covered by tests that mutate live settings after capture and assert the bound target stays unchanged (`capture_clones_and_normalizes_selected_profile_for_console_binding()` in `src/test/kotlin/com/queukat/livy_new/LivyExecutionTargetTest.kt`).

### 3. Route the right snippet and preserve source origin

- For context-menu opening, `RunCodeViaLivyAction.resolveOpenRequest()` chooses selection if present, otherwise whole-file text, and replaces the work-file contents with that snippet (`src/main/kotlin/com/queukat/livy_new/RunCodeViaLivyAction.kt`).
- For direct run actions, the routed snippet is attached as a `LivyWorkSurfaceRequest` with `autorun = true` and `contentMode = NONE`, so the reusable work file can stay intact while a source snippet is executed immediately (`src/main/kotlin/com/queukat/livy_new/RunSelectionOrLineInLivyAction.kt`, `src/main/kotlin/com/queukat/livy_new/RunFileInLivyAction.kt`, `src/main/kotlin/com/queukat/livy_new/editor/ui/LivyConsolePanel.kt`).
- `LivySourceOrigin` captures the source path, source name, snippet mode, and optional line range so the console can later label results and navigate back to the origin (`src/main/kotlin/com/queukat/livy_new/LivySourceRouting.kt`).
- Source resolution prefers selection over current line, and SQL current-statement routing is a semicolon-based heuristic around the caret rather than a parser (`resolveSelectionOrCurrentLine()`, `resolveSqlStatementAtCaret()` in `src/main/kotlin/com/queukat/livy_new/LivySourceRouting.kt`; tests in `src/test/kotlin/com/queukat/livy_new/LivySourceRoutingTest.kt`).

### 4. Edit and run from the console

- The custom file editor is `LivyConsoleFileEditor`, which hosts `LivyConsolePanel` and applies any pending work-surface request on open (`src/main/kotlin/com/queukat/livy_new/editor/LivyConsoleFileEditor.kt`).
- `LivyConsolePanel` builds a toolbar with `Run Current`, `Run File`, `Cancel`, `Show Logs`, `History`, and `Open Source`, making the work file the main iterative surface (`src/main/kotlin/com/queukat/livy_new/editor/ui/LivyConsolePanel.kt`).
- `Run Current` behavior is mode-sensitive:
  - selection first;
  - if the work-file mode is SQL, use the semicolon-delimited statement around the caret;
  - otherwise fall back to the whole work file (`LivyConsolePanel.currentRunnableText()` and `LivyWorkFileMode.statementAwareRun` in `src/main/kotlin/com/queukat/livy_new/editor/ui/LivyConsolePanel.kt` and `src/main/kotlin/com/queukat/livy_new/LivyWorkFileMode.kt`).
- `Run File` always resends the full work-file text (`RunFileAction.actionPerformed()` in `src/main/kotlin/com/queukat/livy_new/editor/ui/LivyConsolePanel.kt`).

### 5. Create or reuse a managed session, then wait for the statement

- On execution, the panel remembers the current draft, marks the UI as running, then starts a cancellable background task (`executeCode()` in `src/main/kotlin/com/queukat/livy_new/editor/ui/LivyConsolePanel.kt`).
- The background task gets a `LivyClient` for the captured `baseUrl`, then hands session acquisition to `SessionManager` using the captured profile snapshot, not current global settings (`LivyConsolePanel.executeCode()`, `SessionManager.kt`, `LivyClientProvider.kt`).
- After session acquisition, the code is submitted with `LivyClient.runStatement()`, and the panel polls `LivyClient.getStatement()` until the state becomes `available`, `error`, or `cancelled`, with a ten-minute timeout (`LivyConsolePanel.waitForStatementAvailable()` in `src/main/kotlin/com/queukat/livy_new/editor/ui/LivyConsolePanel.kt`).

### 6. Inspect results inside the editor

- Each finished run becomes a new result tab inside the same console via `addResultTab()` (`src/main/kotlin/com/queukat/livy_new/editor/ui/LivyConsolePanel.kt`).
- Every result panel includes:
  - `Raw` JSON output with profile/server/session/source metadata;
  - `Pretty` text;
  - `Table` when ASCII table output is detected;
  - `Error` tab when Livy returns an error with traceback (`createOutputPanel()` in `src/main/kotlin/com/queukat/livy_new/editor/ui/LivyConsolePanel.kt`).
- Result panels also expose `Reuse Code`, `Inspect`, `Source`, and `Copy Raw` actions, keeping iteration inside the work surface (`createOutputPanel()` in `src/main/kotlin/com/queukat/livy_new/editor/ui/LivyConsolePanel.kt`).

### 7. Reuse local history and drafts

- Empty work files restore the last saved draft for the same profile and kind when local history is enabled (`resolveInitialConsoleText()` in `src/main/kotlin/com/queukat/livy_new/LivyConsolePersistence.kt`, `LivyConsolePanel.initialConsoleText()`).
- Every successful or failed start records snippet history with profile name, base URL, kind, status, and optional session/statement ids (`LivyPluginSettings.recordConsoleHistory()` in `src/main/kotlin/com/queukat/livy_new/LivyPluginSettings.kt`).
- `LivyHistoryDialog` lets the user inspect, insert, replace, rerun, copy, or clear saved snippets for the bound profile (`src/main/kotlin/com/queukat/livy_new/LivyHistoryDialog.kt`).

### 8. Inspect logs and statements

- From the console, `Show Logs` uses the `lastUsedSession` reference, which stores the exact `baseUrl` and session id of the last execution, then opens `SessionLogsDialog` against that server (`LivyConsolePanel.showLogs()`, `LivyExecutionTarget.kt`, `SessionLogsDialog.kt`).
- `SessionLogsDialog` loads up to 5000 log lines and provides client-side search with next/prev navigation (`SessionLogsDialog.loadLogsAsync()` in `src/main/kotlin/com/queukat/livy_new/SessionLogsDialog.kt`).
- The sessions tool window is created by `LivySessionsWindowFactory` and hosts `LivySessionsPanel` (`src/main/kotlin/com/queukat/livy_new/LivySessionsWindowFactory.kt`, `src/main/kotlin/com/queukat/livy_new/bottompanel/LivySessionsPanel.kt`).
- `LivySessionsPanel.refreshSessions()` captures a `LivyExecutionTarget` for the selected profile and loads all sessions from that target; statements and logs dialogs launched from the table use the `loadedTarget.baseUrl`, not the current active profile at click time (`src/main/kotlin/com/queukat/livy_new/bottompanel/LivySessionsPanel.kt`, `src/main/kotlin/com/queukat/livy_new/LivyClient.kt`).
- `ShowStatementsDialog` lists up to 50 recent statements in descending order, can inspect a selected statement in detail, copy its code, or reopen that code in a work file bound to the same `executionTarget` (`src/main/kotlin/com/queukat/livy_new/ShowStatementsDialog.kt`, `src/main/kotlin/com/queukat/livy_new/LivyStatementDetailsDialog.kt`).

## What is especially article-worthy in this flow

- It is a real loop, not a single button: source selection, routed execution, result inspection, history reuse, logs, statements, and sessions all stay inside IDE surfaces (`Run*Action` classes, `LivyConsolePanel`, `LivyHistoryDialog`, `SessionLogsDialog`, `ShowStatementsDialog`, `LivySessionsPanel`).
- It is intentionally not notebook-like: the work file is reusable and language-aware, but execution semantics stay narrow and explicit (`LivyWorkFileMode.kt`, `LivySourceRouting.kt`, `README.md`, `plugin.xml`).

## А не фигню ли я делаю?

- Real user workflow focus: yes, this is a concrete start-to-finish loop a user can actually follow.
- IDE productivity tool, not platform: yes, the flow is framed as routing/editing/executing/inspecting inside the IDE.
- Overclaiming security/auth or IntelliJ internals: no, the flow sticks to observable actions, dialogs, and state objects.
- Is managed-session reuse really strongest: yes, but it now sits inside a fuller workflow story instead of standing alone.
- Enough material for one focused article: yes, this file alone shows a coherent article spine.
