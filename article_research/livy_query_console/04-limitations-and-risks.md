# Limitations and Risks

## Summary table

| Topic | Verdict | Evidence | Article handling |
| --- | --- | --- | --- |
| App-global settings | Implemented risk | `LivyPluginSettings` is an app-level service persisted to `LivyPluginSettings.xml`; actions and sessions UI update active profile globally (`src/main/kotlin/com/queukat/livy_new/LivyPluginSettings.kt`, `RunCodeViaLivyAction.kt`, `RunSelectionOrLineInLivyAction.kt`, `RunFileInLivyAction.kt`, `bottompanel/LivySessionsPanel.kt`) | Mention directly; do not imply project-scoped isolation |
| Auth / secure credential storage | Missing | Settings UI and metadata explicitly disclaim auth headers/tokens/cookies, Kerberos, OAuth, and secure credential storage; no auth client customizers were found in `LivyClientProvider.kt` or `LivyClient.kt` | Mention directly; this is one of the repo’s sharpest honesty boundaries |
| Connection profiles | Not missing in repository | Multiple named profiles are implemented in settings, tests, and docs (`LivyPluginSettings.kt`, `LivyPluginConfigurable.kt`, `LivyProfilesTest.kt`, `README.md`) | Replace the requested claim with a more precise limitation: profiles exist, but are app-global and direct-connect only |
| Persistent console history | Narrow implementation only | Local snippet history and last-draft restore exist, but notebook-like result/session timeline persistence was not found in repository (`LivyPluginSettings.kt`, `LivyHistoryDialog.kt`, `LivyConsolePersistence.kt`) | Be precise: snippet persistence exists; persistent execution narrative does not |
| Statement drill-down depth | Limited | Statements dialog lists only up to 50 recent statements with shallow columns, then a separate details dialog (`ShowStatementsDialog.kt`, `LivyClient.listStatements()` in `LivyClient.kt`) | Mention as a real constraint, not as a total absence |
| Follow-up actions rebinding to current global settings | Not found in main flows | Console logs use stored `LivySessionRef.baseUrl`; sessions panel dialogs use `loadedTarget.baseUrl`; statement reopen uses existing `executionTarget` (`LivyConsolePanel.kt`, `LivyExecutionTarget.kt`, `bottompanel/LivySessionsPanel.kt`, `ShowStatementsDialog.kt`) | Say "not found in repository" and replace with the actual residual risk below |
| Deeper IDE language semantics | Missing | Work-file mode reuses host file types/highlighting when available, but no parser/completion/inspection/refactoring classes were found in `src/main/kotlin`; SQL current statement uses a semicolon heuristic (`LivyWorkFileMode.kt`, `LivySourceRouting.kt`, `README.md`, `plugin.xml`) | Mention directly; do not oversell "IDE intelligence" |

## Detailed notes

### 1. Settings are app-global, not project-scoped

- `LivyPluginSettings` is declared as `@Service(Service.Level.APP)` and persisted through `Storage("LivyPluginSettings.xml")`, which makes configuration application-wide rather than project-local (`src/main/kotlin/com/queukat/livy_new/LivyPluginSettings.kt`).
- Actions that choose a profile also call `settings.setActiveProfile(...)`, so launching a work file or run flow updates the globally active profile for future flows (`RunCodeViaLivyAction.kt`, `RunSelectionOrLineInLivyAction.kt`, `RunFileInLivyAction.kt`).
- The sessions tool window also mutates the active profile when the combo-box selection changes, even before the next refresh (`bottompanel/LivySessionsPanel.kt`).

Implication for the article:
- say "profiles are app-global and flow-bound by snapshot";
- do not say "project environments" unless future code adds that.

### 2. Advanced auth and secure storage are absent

- The settings screen itself says current support is "direct HTTP(S) connectivity only" and explicitly says auth headers/tokens/cookies, secure credential storage, Kerberos, and OAuth are not supported yet (`LivyPluginConfigurable.createComponent()` in `src/main/kotlin/com/queukat/livy_new/LivyPluginConfigurable.kt`).
- `plugin.xml` repeats the same limitation in user-facing metadata (`src/main/resources/META-INF/plugin.xml`).
- `LivyClientProvider` creates a plain shared `OkHttpClient` with timeouts only; no repository evidence was found for interceptors, cookie jars, authenticators, or custom SSL handling in client code (`src/main/kotlin/com/queukat/livy_new/LivyClientProvider.kt`, search result: not found in repository).
- README’s SSL section pushes certificate/truststore handling to the JetBrains Runtime / IDE environment rather than implementing custom trust management in the plugin (`README.md`).

Important nuance:
- `proxyUser` exists, but that is part of the Livy session payload, not proof of HTTP-level auth support (`LivySessionSpec.kt`, `LivyModels.kt`, `LivyPluginConfigurable.kt`).

### 3. "Missing connection profiles" is not accurate anymore

- Named connection profiles are clearly implemented:
  - stored in `PluginState.profiles` with `activeProfileId` and `defaultProfileId` (`LivyPluginSettings.kt`);
  - editable in `LivyPluginConfigurable` with add/delete/select/default/active actions (`LivyPluginConfigurable.kt`);
  - covered by migration and active/default tests (`LivyProfilesTest.kt`);
  - described in README and `plugin.xml`.

More accurate limitation:
- profiles are still direct-connect only, app-global, and not secret-bearing environment bundles (`LivyPluginConfigurable.kt`, `README.md`, `plugin.xml`).

### 4. Persistent console history exists, but only as local snippet/draft persistence

- Implemented:
  - bounded local snippet history per profile (`LivyPluginSettings.recordConsoleHistory()` and `historyEntriesForProfile()` in `src/main/kotlin/com/queukat/livy_new/LivyPluginSettings.kt`);
  - last-draft restore per profile and kind (`rememberConsoleDraft()`, `draftTextForProfile()`, `resolveInitialConsoleText()`).
- Not found in repository:
  - persistence of result tabs across IDE restarts;
  - persistence of full statement/result timeline inside the console UI;
  - notebook-style ordered execution narrative restore.

Editorial treatment:
- use the phrase "local snippet history and last-draft restore";
- avoid the stronger phrase "persistent console history" unless clarified.

### 5. Statement and log drill-down are useful but still shallow

- `ShowStatementsDialog` fetches only `size = 50` statements in descending order and shows only `ID`, shortened `Code`, `State`, and `Output Status` in its table (`src/main/kotlin/com/queukat/livy_new/ShowStatementsDialog.kt` and `src/main/kotlin/com/queukat/livy_new/LivyClient.kt`).
- Deeper data requires opening `LivyStatementDetailsDialog`, which is helpful but still a one-statement detail surface rather than a richer browser with search, filters, or pagination (`src/main/kotlin/com/queukat/livy_new/LivyStatementDetailsDialog.kt`).
- `SessionLogsDialog` loads up to 5000 lines in one request and provides client-side search only (`src/main/kotlin/com/queukat/livy_new/SessionLogsDialog.kt`).

So the honest claim is:
- statement/log inspection is implemented and useful;
- it is not yet deep observability tooling.

### 6. Requested risk: rebinding follow-up actions to current global settings

Status: **not found in repository for the main execution/logs/statements flows.**

Counterevidence:
- console log viewing uses `lastUsedSession.baseUrl`, not current active profile (`LivyConsolePanel.showLogs()` in `src/main/kotlin/com/queukat/livy_new/editor/ui/LivyConsolePanel.kt`);
- sessions tool window stores a `loadedTarget` and uses that target’s `baseUrl` for statements/logs dialogs (`bottompanel/LivySessionsPanel.kt`);
- statement reopen flows pass the existing `executionTarget` into `openLivyConsole()` (`ShowStatementsDialog.kt`, `LivyStatementDetailsDialog.kt`).

The actual nearby risk is different:
- selecting another profile in the sessions tool window updates the global active profile for **future** work-file flows, even though currently loaded sessions remain tied to `loadedTarget` until refresh (`bottompanel/LivySessionsPanel.kt`).

### 7. There is no deeper Spark-aware IDE language layer

- `LivyWorkFileMode` maps execution kind to preferred file extensions and reuses existing host-IDE file types when available (`src/main/kotlin/com/queukat/livy_new/LivyWorkFileMode.kt`).
- `LivyConsolePanel` reports this as "Editor highlighting" and falls back to plain text when necessary (`src/main/kotlin/com/queukat/livy_new/editor/ui/LivyConsolePanel.kt`).
- The repo implements a custom file type/editor provider, but no parser, completion contributor, inspection, reference resolver, lexer, or refactoring support classes were found in `src/main/kotlin` (search result: not found in repository).
- SQL current-statement support is explicitly a semicolon heuristic, not parsing (`LivySourceRouting.kt`, `README.md`, `plugin.xml`).

Implication:
- "language-aware, not language-smart" is a defensible phrase because the repo itself uses that framing (`README.md`).

## Not included (because not supported by repository evidence)

- Claim that profiles are missing
- Claim that follow-up actions usually hit the wrong server
- Claim that history is equivalent to notebook persistence
- Claim that proxy user equals enterprise auth support

## А не фигню ли я делаю?

- Real user workflow focus: yes, every limitation here is framed in terms of what a user can or cannot rely on.
- IDE productivity tool, not platform: yes, limitations are about workflow boundaries, not platform comparison theater.
- Overclaiming security/auth or IntelliJ internals: no, this file is deliberately conservative and uses "not found in repository" where needed.
- Is managed-session reuse really strongest: yes, and these limitations help keep that angle credible rather than inflated.
- Enough material for one focused article: yes, the limitation section is now sharp enough to keep the article honest.
