# Article Outline

## Recommended angle

Recommended angle: **Keeping remote Spark exploration inside the IDE, with safe managed-session reuse as the engineering idea that makes the loop credible.**

Why this angle wins:
- it matches the repo’s own positioning (`README.md`, `plugin.xml`);
- it stays honest about scope;
- it gives the article both a user story and a concrete technical differentiator (`LivyConsolePanel.kt`, `SessionManager.kt`, `LivySessionSpec.kt`).

## Outline

### 1. The real problem is the Livy context switch, not Spark theory

- Key claim: the plugin exists to collapse the manual Livy REST loop back into the IDE.
- Key evidence: `LivyClient.kt` wraps session creation, statement submission, polling, logs, and statement listing; `Run*Action` classes route editor content into that flow.
- Why it matters: it gives the article a grounded productivity problem instead of a generic plugin introduction.

### 2. The product is a reusable work file, not a fake notebook

- Key claim: the central UI is a reusable work file with source-aware routing and result tabs.
- Key evidence: `RunCodeViaLivyAction.kt`, `LivyConsoleLauncher.kt`, `editor/LivyConsoleFileEditor.kt`, `editor/ui/LivyConsolePanel.kt`.
- Why it matters: this is how the plugin stays useful without pretending to implement notebook persistence.

### 3. Snapshot binding is what keeps profile changes from becoming chaos

- Key claim: new work files and session refreshes bind to a captured `LivyExecutionTarget`, not live mutable settings.
- Key evidence: `LivyExecutionTarget.kt`, `LivyExecutionTargetTest.kt`, context labels in `LivyConsolePanel.kt`, scope text in `README.md` and `plugin.xml`.
- Why it matters: it explains why multiple profiles can exist without silently retargeting an active exploration surface.

### 4. “Run Current” is intentionally narrow and honest

- Key claim: the plugin supports selection/current-line/whole-file flows plus a narrow SQL statement heuristic, not Spark-aware parsing.
- Key evidence: `LivySourceRouting.kt`, `LivyWorkFileMode.kt`, `LivySourceRoutingTest.kt`, `README.md`.
- Why it matters: it demonstrates product restraint and prevents inflated IntelliJ-language-tooling claims.

### 5. Safe managed-session reuse is the real engineering differentiator

- Key claim: reuse applies only to plugin-managed sessions on the same server with matching config fingerprint.
- Key evidence: `SessionManager.kt`, `LivyManagedSessions.kt`, `LivySessionSpec.kt`, `SessionManagerTest.kt`, `LivyManagedSessionsTest.kt`, `LivySessionSpecFactoryTest.kt`.
- Why it matters: it gives the article a concrete technical center instead of “we made a console.”

### 6. Results, logs, statements, and history complete the inner loop

- Key claim: the plugin is useful because execution is only part of the loop; inspection and reuse stay nearby.
- Key evidence: `LivyConsolePanel.kt`, `SessionLogsDialog.kt`, `ShowStatementsDialog.kt`, `LivyStatementDetailsDialog.kt`, `LivyHistoryDialog.kt`, `LivyPluginSettings.kt`.
- Why it matters: it turns the story from one-shot submission into an iterative remote exploration workflow.

### 7. The repo’s honesty is part of the story

- Key claim: the repository is unusually explicit about what it is not.
- Key evidence: auth/scope/tooling disclaimers in `README.md`, `plugin.xml`, and `LivyPluginConfigurable.kt`; absence of parser/completion/auth client customizers in `src/main/kotlin`.
- Why it matters: the article becomes more trustworthy if it foregrounds boundaries instead of hiding them.

### 8. Where the design is still thin

- Key claim: the workflow is credible today, but there are real limits around app-global settings, direct-connect auth, shallow drill-down, and lack of notebook-style persistence.
- Key evidence: `LivyPluginSettings.kt`, `LivyPluginConfigurable.kt`, `ShowStatementsDialog.kt`, `SessionLogsDialog.kt`, repository search results.
- Why it matters: readers need a realistic adoption filter, and this keeps the tone technical rather than promotional.

## Section order note

If the article needs a more engineering-heavy opening, sections 3 and 5 can swap places:
- lead with managed-session reuse if the audience is more backend/internals-oriented;
- keep the current order if the audience is broader JetBrains/data-engineering readers.

## А не фигню ли я делаю?

- Real user workflow focus: yes, each section advances the user’s remote exploration loop.
- IDE productivity tool, not platform: yes, the outline never turns into cluster platform marketing.
- Overclaiming security/auth or IntelliJ internals: no, the outline explicitly reserves a section for constraints and absent features.
- Is managed-session reuse really strongest: yes, it is the main engineering centerpiece inside the larger workflow story.
- Enough material for one focused article: yes, eight sections are enough without needing filler.
