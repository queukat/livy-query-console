# Problem and Baseline

## Baseline workflow without the plugin

Likely baseline, inferred from the Livy REST calls this plugin automates:

1. Prepare or copy a Spark, PySpark, or SQL snippet in the IDE.  
   Evidence: the plugin’s execution path is built around routing editor snippets or full files into Livy (`RunCodeViaLivyAction`, `RunSelectionOrLineInLivyAction`, `RunFileInLivyAction`, `LivySourceRouting.kt`).
2. Create or identify a Livy interactive session manually.  
   Evidence: the client explicitly calls `POST /sessions` via `LivyClient.createSession()` and later `GET /sessions/{id}` via `LivyClient.getSession()` (`src/main/kotlin/com/queukat/livy_new/LivyClient.kt`).
3. Submit code as a statement manually.  
   Evidence: the client explicitly calls `POST /sessions/{id}/statements` in `LivyClient.runStatement()` (`src/main/kotlin/com/queukat/livy_new/LivyClient.kt`).
4. Poll for completion manually.  
   Evidence: the plugin itself has to poll `GET /sessions/{id}/statements/{statementId}` in `LivyConsolePanel.waitForStatementAvailable()` (`src/main/kotlin/com/queukat/livy_new/editor/ui/LivyConsolePanel.kt`).
5. Pull logs or statement lists separately when something goes wrong.  
   Evidence: the plugin wraps `GET /sessions/{id}/log` and `GET /sessions/{id}/statements` in `LivyClient.getSessionLogs()` and `LivyClient.listStatements()` (`src/main/kotlin/com/queukat/livy_new/LivyClient.kt`).

The repository does not include an embedded Spark cluster, notebook engine, or alternate execution back-end; the value proposition is reducing friction around this Livy REST loop from inside the IDE (`README.md`, `src/main/resources/META-INF/plugin.xml`, `src/main/kotlin/com/queukat/livy_new/LivyClient.kt`).

## Why curl/Postman/browser/shell loops are painful

- They break source context. The plugin records where a snippet came from, including selection/current-line/file and line numbers, because the baseline otherwise loses that context (`LivySourceOrigin`, `resolveSelectionOrCurrentLineRequest()`, `resolveWholeFileRequest()` in `src/main/kotlin/com/queukat/livy_new/LivySourceRouting.kt`).
- They force manual session judgment. The repo has dedicated code for deciding whether a session is safe to reuse, which implies that manual reuse is error-prone enough to deserve automation (`SessionManager.getSession()`, `LivyManagedSessions.matchingSessionIds()` in `src/main/kotlin/com/queukat/livy_new/SessionManager.kt` and `src/main/kotlin/com/queukat/livy_new/LivyManagedSessions.kt`).
- They split results, logs, and statements away from the editing surface. The plugin adds result tabs, logs dialogs, statements dialogs, and a sessions tool window because the baseline Livy flow is otherwise fragmented (`LivyConsolePanel`, `SessionLogsDialog`, `ShowStatementsDialog`, `LivySessionsPanel`).
- They make small experiments expensive. The plugin explicitly optimizes for "selection or line", "whole file", "Run Current", and snippet history, all of which signal a high-frequency iterate-check-adjust workflow rather than batch submission (`RunSelectionOrLineInLivyAction`, `RunFileInLivyAction`, `LivyConsolePanel.currentRunnableText()`, `LivyHistoryDialog`).

## Which users actually benefit

- JetBrains users who already work in IntelliJ-based IDEs and have direct access to a Livy endpoint.  
  Evidence: installation scope is IntelliJ Platform IDEs, and the settings UI explicitly says "direct HTTP(S) connectivity only" (`README.md`, `plugin.xml`, `LivyPluginConfigurable.createComponent()`).
- Data engineers or Spark users doing short-lived remote checks rather than full notebook storytelling.  
  Evidence: the plugin exposes snippet routing, result tabs, statement/log inspection, and local snippet history, but does not implement notebook persistence or semantic tooling (`README.md`, `plugin.xml`, `LivyConsolePanel`, `LivyHistoryDialog`).
- Teams that need the cluster-side truth quickly from their editor.  
  Evidence: executions go straight to Livy sessions/statements, and session logs/statements can be inspected from the IDE (`LivyClient.kt`, `SessionLogsDialog.kt`, `ShowStatementsDialog.kt`).

## When this workflow matters

- When the question is "what does this do on the remote Spark/Livy side right now?" rather than "how do I build a polished notebook?"  
  Evidence: current scope is repeatedly described as lightweight execution workflow, not notebook persistence or semantic Spark tooling (`README.md`, `plugin.xml`, `LivyPluginConfigurable.kt`).
- When the experiment is short enough that opening another tool feels like drag.  
  Evidence: there are dedicated entrypoints for selection/current line/whole file and a reusable work file rather than only a full-submit dialog (`RunCodeViaLivyAction.kt`, `RunSelectionOrLineInLivyAction.kt`, `RunFileInLivyAction.kt`, `LivyConsoleLauncher.kt`).
- When session reuse matters, but unsafe reuse would be worse than no reuse.  
  Evidence: a substantial part of the codebase is dedicated to safe managed-session tracking and matching (`SessionManager.kt`, `LivyManagedSessions.kt`, `LivySessionSpec.kt`, `SessionManagerTest.kt`).

## Scope guard for the article

- Do describe this as an IDE productivity loop over Livy.  
  Evidence: plugin name, README positioning, and `plugin.xml` description all use this framing.
- Do not describe it as a Spark platform, notebook replacement, or rich IDE language integration layer.  
  Evidence: README/plugin metadata explicitly disclaim notebook semantics, advanced auth, and semantic tooling; no parser/completion/inspection classes were found in `src/main/kotlin`.

## А не фигню ли я делаю?

- Real user workflow focus: yes, this note stays on the manual Livy loop the plugin compresses.
- IDE productivity tool, not platform: yes, the baseline is framed as editor-to-Livy friction, not platform replacement.
- Overclaiming security/auth or IntelliJ internals: no, claims are tied to visible REST/client/UI code only.
- Is managed-session reuse really strongest: yes, because manual session handling is exactly where the remote loop becomes risky.
- Enough material for one focused article: yes, the problem statement already points to a credible, narrow story.
