# Fix Plan

## Executive Summary
- Goal: move the plugin to a more honestly shipped state by fixing real correctness/product gaps first, then supportability/CI/tests, then low-risk UX/repo cleanup.
- Current audit result on 2026-03-30: the main P0 bug is still present. Console execution and follow-up actions are not bound to the Livy server/session context that the console started with. They still recreate clients from current global settings in critical paths.
- Current boundary is still narrow: one global connection configuration, no connection profiles, no auth model, no credential storage, no explicit UX guardrails for secured/multi-environment setups.
- CI is better than before because `verifyPlugin` is already in `ci.yml` and `release.yml`, but the publish workflow still publishes without rerunning the same quality gate.
- Repo hygiene still has at least one honest mismatch: `README.md` claims MIT, but there is no `LICENSE` file in the repo.

## Scope / Non-goals
### In scope
- Fix server/session context binding for console execution and follow-up actions.
- Add regression tests around wrong-server and stale-settings behavior.
- Tighten CI/publish verification where it materially improves confidence.
- Make the supported product boundary explicit if profiles/auth are still out of scope.
- Add low-risk UX cues that help users understand current server/session context.
- Align repo/docs hygiene with reality.

### Non-goals
- No large connection-profile architecture unless it turns out to be the smallest safe fix.
- No auth subsystem, secure credential storage, or enterprise-ready claims that the code does not support.
- No broad IntelliJ editor redesign, PSI integration, or notebook-like workflow.
- No repo-wide refactor just to make the code look cleaner.

## Findings to fix
| ID | Priority | Finding | Status | Notes |
|----|----------|---------|--------|-------|
| F1 | P0 | `LivyConsolePanel` execution, cancel, and show-logs flows recreate clients from current global settings instead of a console-bound server context. | confirmed | Main correctness bug from the audit is still live. |
| F2 | P0 | `SessionManager` still reads mutable app-global settings during orchestration, so a console run is not fully bound to a stable session configuration snapshot. | confirmed | Needs a local ownership fix, not a big redesign. |
| F3 | P1 | Wrong-server/stale-settings regressions are not covered by tests. `SessionManager` tests are still too shallow for orchestration risk. | confirmed | Existing tests cover managed-session registry/specs, not end-to-end orchestration semantics. |
| F4 | P1 | `publish-marketplace.yml` can publish without rerunning the same verification gate used in CI/release. | fixed | Publish now reruns `test buildPlugin verifyPlugin` before `publishPlugin`. |
| F5 | P1 | Product boundary around profiles/auth is still implicit and therefore misleading for secured or multi-environment Livy usage. | fixed | Boundary is now explicit in settings, README, and plugin metadata. |
| F6 | P2 | Console and sessions UI do not clearly show which server/session context they are operating on. | fixed | Bound server/session context is now surfaced in the UI. |
| F7 | P2 | README claims MIT, but no `LICENSE` file exists. | fixed | Added `LICENSE`. |
| F8 | P2 | `buildSearchableOptions` Windows issue is mentioned in the audit history but not yet revalidated in this pass. | mitigated | Revalidated, reproduced, and worked around for Windows local builds by skipping searchable-options tasks there while keeping Linux CI/release unchanged. |

## Prioritized plan
1. Fix P0 context ownership:
   bind console execution to a concrete server URL and stable session settings snapshot; make follow-up actions use the bound context instead of live globals.
2. Add regression tests:
   cover `SessionManager` orchestration, wrong-server reuse, stale-global-settings binding, and realistic create/reuse/delete flows.
3. Tighten CI/publish verification:
   make the publish workflow rerun the same verification gate before publish.
4. Close the product boundary honestly:
   document and surface that the plugin currently supports a single global connection configuration and does not implement auth/profiles.
5. Add low-risk UX polish:
   show active server/session context and improve empty/disabled wording where it clarifies actual behavior.
6. Finish repo hygiene:
   add `LICENSE`, align README, and document any remaining packaging/searchable-options risk honestly.

## Progress checklist
- [x] Audit current code against the requested priorities
- [x] Rebuild `docs/fix_plan.md` as the canonical plan/log artifact
- [x] Fix console/server/session context binding
- [x] Add regression tests for wrong-server / stale-settings behavior
- [x] Recheck and tighten CI/publish verification
- [x] Clarify supported product boundary in UI/docs
- [x] Apply low-risk UX polish tied to the fixed behavior
- [x] Finish repo hygiene and remaining docs alignment
- [x] Run verification and log remaining risks honestly

## Repair log
### 2026-03-30 - Audit baseline
- Re-read the repo instead of trusting the previous plan file.
- Confirmed that the critical wrong-server bug is still live in `LivyConsolePanel`: execution uses `fromSettings()` and follow-up actions recreate clients from current global settings.
- Confirmed that `SessionManager` still reaches back into mutable global settings during orchestration instead of operating on a stable execution snapshot.
- Confirmed that CI/release already run `verifyPlugin`, but publish still does not.
- Confirmed that profiles/auth are still absent as real product features, and `LICENSE` is still missing.

### 2026-03-30 - P0 context binding repair
- Added an explicit console execution target that snapshots the server URL and execution settings when a console tab is opened.
- Switched console execution, cancel, and show-logs flows to use bound server/session references instead of current global settings.
- Split `SessionManager` ownership so execution policy comes from a stable snapshot while managed-session bookkeeping still writes to shared persistent state.
- Bound the sessions tool window to the server it last loaded from, so statement/log dialogs no longer silently drift after a settings change.
- Added lightweight UI context text so users can see which server/session context a console or loaded sessions table is actually using.
- Verification so far: `./gradlew.bat test --console=plain` compiles and passes after the P0 refactor.

### 2026-03-30 - P0 regression coverage
- Added `SessionManagerTest` with orchestration scenarios for:
  same-server reuse, wrong-server no-reuse/create-new, managed-session eviction only on the bound server, and failed session creation cleanup.
- Added `LivyExecutionTargetTest` to verify that console execution captures a normalized, isolated settings snapshot instead of drifting with later global changes.
- Verification so far: `./gradlew.bat test --console=plain` passes with the new tests.

### 2026-03-30 - CI, product boundary, and packaging follow-up
- Updated `publish-marketplace.yml` so manual publish reruns `test buildPlugin verifyPlugin` before upload.
- Made the supported scope explicit in settings UI, README, and plugin metadata:
  single global connection configuration, existing console tabs keep their captured snapshot, and profiles/auth/secure credential storage are not implemented.
- Added `LICENSE` to match the README claim.
- Revalidated the Windows `buildSearchableOptions` failure, confirmed it still reproduces with the mapped-file error, and switched local Windows builds to skip `buildSearchableOptions`/`jarSearchableOptions` while leaving Linux CI/release builds on the full path.
- Verification so far: `./gradlew.bat --gradle-user-home .gradle-user-home --no-daemon test buildPlugin verifyPlugin --stacktrace` passes on this Windows workspace after the mitigation.

### Sanity-check: а не фигню ли я делаю?
- Что именно я чиню: сначала подтверждённый correctness bug around wrong-server / stale-settings behavior, затем test/CI confidence, затем honest boundary/UX/hygiene.
- Почему это реально приближает к цели: это убирает риск молчаливого выполнения/отмены/логов на другом Livy server и повышает доверие к shipped behavior.
- Не ушёл ли я в косметику вместо блокеров: нет, косметика и hygiene пока только зафиксированы как later-phase work.
- Не создаю ли я лишнюю архитектурную сложность: цель локальный context-binding refactor, а не profile framework.
- Не вру ли я себе про “готовность”: да, пока нельзя. Core correctness и regression coverage ещё не закрыты.

### Sanity-check: а не фигню ли я делаю? (after P0 refactor)
- Что именно я чиню: ownership of console execution context and follow-up actions; same class of drift for the sessions table.
- Почему это реально приближает к цели: it removes the most concrete correctness failure mode from the audit instead of polishing wording around it.
- Не ушёл ли я в косметику вместо блокеров: нет, UI text was added only to make the repaired behavior legible.
- Не создаю ли я лишнюю архитектурную сложность: changes stay local to execution target, session manager inputs, and server-bound refs; no profile subsystem was introduced.
- Не вру ли я себе про “готовность”: still no. Regression tests and end-to-end verification are the next gating step.

### Sanity-check: а не фигню ли я делаю? (after regression tests)
- Что именно я чиню: I am locking the repaired ownership model in place with tests on the actual orchestration layer.
- Почему это реально приближает к цели: the new tests directly target the wrong-server/stale-settings class of failures instead of only checking helper functions.
- Не ушёл ли я в косметику вместо блокеров: нет, this is still core correctness confidence work.
- Не создаю ли я лишнюю архитектурную сложность: only a small client interface was introduced so `SessionManager` can be exercised deterministically.
- Не вру ли я себе про “готовность”: still no. CI/publish verification and honest product boundary are still open.

### Sanity-check: а не фигню ли я делаю? (before close-out)
- Что именно я чиню: final supportability and honesty gaps around publish verification, supported scope, and local packaging friction.
- Почему это реально приближает к цели: it removes misleading behavior/claims and makes the repo/build story match what the plugin can actually support today.
- Не ушёл ли я в косметику вместо блокеров: нет, cosmetic work stayed subordinate to correctness/tests; even the Windows packaging change was driven by reproducible build failure.
- Не создаю ли я лишнюю архитектурную сложность: no large profile/auth architecture was added; the packaging mitigation is intentionally small and OS-scoped.
- Не вру ли я себе про “готовность”: better, but still not “enterprise-ready”. The plugin is now more coherent and honest about its limits.

## Verification log
- 2026-03-30: static audit completed against current workspace files.
- 2026-03-30: `./gradlew.bat test --console=plain` passed after the P0 refactor.
- 2026-03-30: `./gradlew.bat test --console=plain` passed again after adding the new regression tests.
- 2026-03-30: `./gradlew.bat --gradle-user-home .gradle-user-home --no-daemon test buildPlugin verifyPlugin --stacktrace` passed after the CI/boundary/packaging changes.

## Remaining risks / deferred items
- Profiles/auth remain unsupported until explicitly implemented; current pass should make that boundary honest rather than pretending otherwise.
- Windows local builds now skip searchable-options generation to avoid the reproducible mapped-file failure. Linux CI/release builds still run the full path, so local Windows ZIPs may differ slightly in settings-search discoverability.
- `LightVirtualFile` usage remains a platform smell, but it is not the first blocker compared with wrong-server correctness.

## Phase 2 - Connection Profiles
### Executive Summary
- Next goal: move the plugin from a single-global-connection model to a usable multi-profile model without regressing the repaired execution-context binding.
- Constraint: profiles first, not enterprise auth. The code should become more useful for real multi-environment usage while staying explicit that advanced auth and secure credential storage remain out of scope.
- Expected outcome: multiple named profiles, explicit profile choice for new console/sessions flows, stable profile snapshot binding for runtime actions, and honest unsupported-auth messaging.

### Scope
- Introduce multiple connection profiles in persistent settings.
- Migrate old single-config state into one default profile automatically.
- Let the user explicitly choose a profile for new console flows and sessions loading.
- Keep console/session follow-up actions bound to a captured profile snapshot rather than live global state.
- Update UI/docs/metadata to describe supported connectivity/auth scope honestly.

### Non-goals
- No secrets subsystem, PasswordSafe integration, OAuth, Kerberos, cookies, or custom auth transports in this phase.
- No giant connections management framework or repo-wide rewrite.
- No claim that the plugin is auth-ready when it only supports direct basic connectivity.

### Proposed profile model
- `ConnectionProfileState`
  - `id`
  - `displayName`
  - `livyServerUrl`
  - current execution/session settings already used by the plugin (`kind`, `proxyUser`, resources, TTL, conf, session strategy, limits)
- `PluginState`
  - `profiles`
  - `activeProfileId`
  - `defaultProfileId`
  - existing shared/global non-profile state that should stay app-wide for now (`managedSessions`, `sessionTableColumns`)
- Runtime binding
  - new console/session loads capture `profileId + profileName + baseUrl + profile settings snapshot`
  - already-open contexts never retarget when active/default profile changes later

### Migration / compatibility notes
- Old persisted single-config settings must transparently become one profile during `loadState`.
- The migrated profile should become both the default and active profile.
- Legacy root-level fields can remain only as migration input; new runtime code should resolve through profiles.
- Managed-session bookkeeping remains keyed by actual server/config compatibility, not by live active profile.

### New phase checklist
- [x] Re-audit current code for single-profile assumptions before editing
- [x] Implement profile data model and legacy migration
- [x] Bind runtime capture to profile snapshots
- [x] Add explicit profile choice for new console/sessions flows
- [x] Add migration and profile-switching regression tests
- [x] Update docs/metadata/settings copy for honest auth/config boundary
- [x] Verify full build/test/plugin checks

### Repair log
#### 2026-03-30 - Phase 2 design note
- Re-read the current execution-target, session-manager, settings, and sessions-panel code before introducing profiles.
- Confirmed that the repaired wrong-server fix is local and can be preserved if profile selection resolves to a captured execution target instead of live settings.
- Confirmed that the biggest remaining product gap is still the single-global-connection model, not missing auth transports.

#### 2026-03-30 - Profile model and migration
- Added a compact `ConnectionProfileState` model with `id`, `displayName`, `livyServerUrl`, and the existing execution/session settings required by the current workflow.
- Added `profiles`, `activeProfileId`, and `defaultProfileId` to `PluginState`, while keeping the old root settings fields only as migration/backward-compatibility mirrors.
- Implemented transparent migration so old single-config state becomes one default/active profile on load.
- Added helper operations for active/default selection, profile removal, labeling, and syncing legacy fields from the active profile.

#### 2026-03-30 - Runtime and UI wiring
- Updated `LivyExecutionTarget` to capture `profileId + profileName + baseUrl + profile settings snapshot`.
- Updated new-console flow to explicitly choose a profile when multiple profiles exist, then bind the opened console to that chosen profile snapshot.
- Updated the sessions tool window to expose a profile selector, keep a separate loaded profile context, and avoid retargeting loaded actions when the selected profile changes later.
- Reworked settings UI from a single global connection form into a profile-aware editor with add/delete, explicit active/default selection, and per-profile parameter editing.

#### 2026-03-30 - Profile regression coverage
- Updated the existing execution/session tests to use profile snapshots rather than root `PluginState`.
- Added profile-specific tests for legacy migration, active/default switching, removal reassignment, and capture binding.
- Verification so far: `./gradlew.bat test --console=plain` passes after the model/runtime/UI/test changes.

#### 2026-03-30 - Docs and metadata alignment
- Updated `README.md` quick-start and supported-boundary sections to describe multiple named profiles, explicit profile selection, snapshot binding, and the still-unsupported auth/storage scenarios.
- Updated `plugin.xml` description so Marketplace metadata no longer describes a single global connection model.
- Kept the boundary narrow on purpose: direct Livy HTTP(S) connectivity with profiles, not advanced auth/platform claims.

#### 2026-03-30 - Verification and screenshot scaffolding
- Kept the screenshot generator test aligned with the new profile-based settings/runtime model so reference UI generation does not depend on removed single-config fields.
- Re-ran the full shipped-path verification gate after the docs/metadata cleanup and final test cleanup.

### Verification log
- 2026-03-30: static review of `LivyPluginSettings`, `LivyExecutionTarget`, `RunCodeViaLivyAction`, `LivySessionsPanel`, and `SessionManager` completed for profile-phase design.
- 2026-03-30: `./gradlew.bat test --console=plain` passed after profile model/runtime/UI/test changes.
- 2026-03-30: `./gradlew.bat --gradle-user-home .gradle-user-home --no-daemon test buildPlugin verifyPlugin --stacktrace` passed after docs/metadata alignment.
- 2026-03-30: `./gradlew.bat --gradle-user-home .gradle-user-home --no-daemon test buildPlugin verifyPlugin --stacktrace` passed again after final screenshot-test cleanup.

### Remaining risks
- If profile editing UI becomes too dynamic, it can bloat the settings page. Need to keep it intentionally simple.
- If runtime code starts reading `activeProfileId` after capture, it will reintroduce the same class of drift bug in a new form.
- If auth wording is sloppy, profiles may look more capable than they really are.

### Sanity-check: а не фигню ли я делаю? (after model/runtime/UI/tests)
- Я правда двигаю plugin к более реальному usage? Да: multi-profile usage is now part of the product model and visible runtime flow, not just documentation.
- Я не начал ли строить auth-platform вместо profiles? Нет: only direct profile/config selection was added; advanced auth remains explicitly unsupported.
- Я не ломаю ли уже закрытый execution-context fix? Нет: captured execution targets now bind to profile snapshots, so the same no-retargeting rule still holds.
- Я не создаю ли слишком тяжёлую модель ради простой задачи? Нет: the model is still small and intentionally flat.
- UI честно отражает возможности или снова обещает лишнее? Mostly yes, but docs/metadata still need to be updated to match the new profiles-first scope.

### Sanity-check: а не фигню ли я делаю? (after docs/metadata cleanup)
- Я правда двигаю plugin к более реальному usage? Да: users can now understand and rely on the actual multi-profile flow instead of reading stale single-connection docs.
- Я не начал ли строить auth-platform вместо profiles? Нет: the wording explicitly keeps auth/storage out of scope.
- Я не ломаю ли уже закрытый execution-context fix? Нет: this pass only aligns docs/metadata with the already-implemented snapshot model.
- Я не создаю ли слишком тяжёлую модель ради простой задачи? Нет: no new runtime abstractions were added here.
- UI честно отражает возможности или снова обещает лишнее? Да: settings UI, README, and Marketplace metadata now describe the same boundary.

### Sanity-check: а не фигню ли я делаю? (before phase close)
- Я правда двигаю plugin к более реальному usage? Да: the plugin now supports separate dev/stage/prod-style Livy targets without forcing users back through one mutable global connection.
- Я не начал ли строить auth-platform вместо profiles? Нет: nothing beyond direct connectivity profiles was added, and unsupported auth/storage remains explicit.
- Я не ломаю ли уже закрытый execution-context fix? Нет: full verification passed with runtime still bound to captured profile snapshots.
- Я не создаю ли слишком тяжёлую модель ради простой задачи? Нет: the model is still a flat profile list plus active/default ids.
- UI честно отражает возможности или снова обещает лишнее? Да: profile selection is usable, and the copy now says exactly where support stops.

## Phase 3 - Console History and Inspectability
### Goal
- Make the console feel like a repeatable daily-work tool rather than a one-shot scratch tab.
- Add lightweight local history, draft persistence, and better result/statement/log drill-down without turning the plugin into a notebook or session-restoration platform.

### Scope
- Add a compact local history model with retention limits and per-profile/context tagging.
- Restore the last local draft for a profile when opening a new empty console for that profile.
- Add a simple history UI inside the console flow for insert/replace/rerun/copy of recent snippets.
- Improve inspectability for statements/results/logs where it directly helps the run -> inspect -> rerun loop.
- Keep all rerun flows bound to the existing captured execution target model.

### Non-goals
- No notebook UX, collaborative documents, or project/workspace persistence.
- No attempt to restore remote Livy sessions automatically after IDE restart.
- No auth/storage system work in this phase.
- No unbounded storage of code/output/history.

### History / persistence design
- Persist a bounded global list of recent history entries with profile tagging rather than a large per-profile storage tree.
- Each history entry should capture:
  - `id`
  - `createdAtMs`
  - `profileId`
  - `profileName`
  - `baseUrl`
  - `languageOrKind`
  - `snippet`
  - `snippetTruncated`
  - `status`
  - optional `sessionId`
  - optional `statementId`
- Persist one last local draft per profile for empty-console restore.
- History UI should live in the console context and default to that console's profile, so rerun/insert actions stay coherent with the bound execution target.
- Reusing a past snippet should execute in the current console's captured profile context, not by rereading live active settings.

### Data retention / privacy notes
- Store snippets locally in the plugin settings file only; do not store auth material or network credentials.
- Keep local history explicitly bounded by an item cap and trim very long snippets before persistence.
- Be honest in UI/docs that this is local plain-text history/draft persistence, not secure storage.
- Prefer lightweight metadata snapshots (`profileName`, `baseUrl`, status ids) over storing bulky outputs.

### New phase checklist
- [x] Re-audit current console/runtime/results code before editing
- [x] Implement lightweight history/draft data model with retention rules
- [x] Wire history recording and draft restore into the console runtime
- [x] Add simple console history UI and rerun/reuse actions
- [x] Improve statement/result/log inspectability
- [x] Add tests for history isolation/retention/rerun behavior
- [x] Update docs/settings/plugin copy for local history boundary
- [x] Verify full build/test/plugin checks

### Repair log
#### 2026-03-30 - Phase 3 design note
- Re-read the current console opener, execution-target binding, file editor, console panel, statements dialog, and logs dialog before changing anything.
- Confirmed that the safest product step is a bounded local history plus per-profile last-draft restore; trying to persist full remote session state would be misleading and much heavier.
- Confirmed that the current results/statements/logs flows already have enough context to add drill-down/reuse without a large viewer framework.

#### 2026-03-30 - History state and persistence seam
- Added a bounded local history model and per-profile draft persistence to `LivyPluginSettings`, including retention caps and snippet-length trimming.
- Kept history plain-text and explicitly local; no outputs, credentials, or auth material are part of the persisted model.
- Added a reusable console-opening helper so future rerun/reuse actions can open a console against an explicit captured execution target instead of falling back to live active settings.
- Added core tests for profile isolation, retention/truncation, and draft persistence behavior before wiring UI on top.

#### 2026-03-30 - Runtime, UI, and inspectability wiring
- Wired the console panel to restore the last local draft for the bound profile when opening an empty console and to keep updating that draft locally as the user edits.
- Added per-profile history recording for executed snippets, including lightweight status and session/statement ids, while keeping execution bound to the current captured target.
- Added a console history dialog with insert/replace/run/copy actions so recent snippets can be reused quickly without pretending to be a notebook.
- Improved inspectability with richer result-tab actions, a statement details dialog, statement-to-console reuse from the sessions flow, and log copy/context metadata.
- Added explicit settings controls for enabling local history, bounding retention, and clearing saved local history/drafts on apply.

#### 2026-03-30 - Docs, metadata, and final verification
- Updated `README.md`, settings copy, and `plugin.xml` metadata to explain that local history/drafts are plain-text local persistence with bounded retention.
- Kept the wording explicit that this is not notebook persistence, not remote session restore, and not secure storage.
- Re-ran the full build/test/verify path after the feature pass and docs cleanup.

### Verification log
- 2026-03-30: static review completed for `RunCodeViaLivyAction`, `LivyExecutionTarget`, `LivyConsoleFileEditor`, `LivyConsolePanel`, `ShowStatementsDialog`, `SessionLogsDialog`, and `LivyPluginSettings` to define the history/inspectability seam.
- 2026-03-30: `./gradlew.bat test --console=plain` passed after adding the history/draft state layer, reusable console opener, and core retention/isolation tests.
- 2026-03-30: `./gradlew.bat test --console=plain` passed again after wiring console history UI, draft restore, statement/result/log drill-down, and the additional restore/snapshot tests.
- 2026-03-30: `./gradlew.bat --gradle-user-home .gradle-user-home --no-daemon test buildPlugin verifyPlugin --stacktrace` passed after the docs/metadata alignment for the history phase.
- 2026-03-30: `./gradlew.bat --gradle-user-home .gradle-user-home --no-daemon test buildPlugin verifyPlugin --stacktrace` passed again after the final statement-details UI cleanup.

### Remaining risks
- If local history stores too much text, the plugin settings file will bloat quickly.
- If rerun/reuse actions read live active settings instead of the bound console target, this phase would reintroduce context drift in a new form.
- If copy/restoration wording is sloppy, users may infer notebook/session restore capabilities that do not exist.
- Local history is plain-text local persistence, so it should not be treated as secure storage for sensitive code or data extracts.

### Sanity-check: а не фигню ли я делаю? (after history state layer)
- Это реально помогает daily workflow? Да: the plugin now has a place to remember recent snippets and last drafts instead of always starting from zero.
- Я не строю ли случайно мини-notebook вместо лёгкой console history? Нет: this is bounded plain-text state only, with no output persistence or remote session restore.
- Я не ломаю ли snapshot/profile binding? Нет: history records against explicit execution targets and a reusable opener now accepts an explicit captured target.
- Я не сохраняю ли лишние чувствительные данные без нужды? Нет: only local snippet text, profile snapshot labels, and lightweight statement/session ids are planned.
- UI честно описывает возможности? Пока частично: state layer is honest, but the actual UI copy still needs to be wired in the next pass.

### Sanity-check: а не фигню ли я делаю? (after runtime/UI wiring)
- Это реально помогает daily workflow? Да: users can restore a draft, inspect a statement/result in more detail, and rerun recent snippets without reconstructing them manually.
- Я не строю ли случайно мини-notebook вместо лёгкой console history? Нет: there is no cell model, shared document, or persisted output notebook; just bounded recent snippets and last-draft restore.
- Я не ломаю ли snapshot/profile binding? Нет: rerun/reuse flows open or execute against explicit captured targets, and the tests still pass after the wiring.
- Я не сохраняю ли лишние чувствительные данные без нужды? Нет: persisted data is still limited to local plain-text snippets/drafts and lightweight execution metadata.
- UI честно описывает возможности? Mostly yes in settings and dialogs; README/plugin metadata still need the final local-history wording pass.

### Sanity-check: а не фигню ли я делаю? (before phase close)
- Это реально помогает daily workflow? Да: the console now supports a practical repeat loop of restore draft -> run -> inspect -> rerun recent snippet.
- Я не строю ли случайно мини-notebook вместо лёгкой console history? Нет: no persisted outputs, no shared workspace model, no remote session resurrection.
- Я не ломаю ли snapshot/profile binding? Нет: full verification passed and all reuse/open flows still go through explicit captured targets.
- Я не сохраняю ли лишние чувствительные данные без нужды? Нет: storage stays bounded and local, and the docs explicitly say it is plain-text, not secure.
- UI честно описывает возможности? Да: settings, README, and Marketplace metadata now describe the same narrow history/persistence boundary.

## Phase 4 - JetBrains-Native Execution Surface
### Goal
- Make the plugin feel more like a native JetBrains editor/workflow tool instead of a separate remote console window.
- Improve IDE entrypoints, editor lifecycle, source-to-execution routing, and navigation back into editable work surfaces without starting a PSI/parser project.

### Scope
- Add IDE-native entrypoints for running selection/current line/whole file where that makes sense.
- Introduce a dedicated reusable Livy work-file flow that is more scratch-like than the current throwaway tab behavior.
- Strengthen source -> execution -> inspect -> reuse -> back-to-source navigation.
- Improve action placement and editor lifecycle where it directly helps the JetBrains workflow.

### Non-goals
- No parser/PSI platform, inspections, completion engine, or semantic language tooling.
- No notebook cells or collaborative document model.
- No auth/storage project or unrelated UI redesign.
- No attempt to support every language/editor scenario with special-case intelligence.

### Proposed platform-integration surface
- Keep one core Livy execution surface, but expose it through:
  - `Open in Livy Work File`
  - `Run Selection or Line in Livy`
  - `Run File in Livy`
- Route source snippets into a profile-bound Livy work file rather than inventing a separate execution model.
- Attach lightweight source-origin metadata (file path/name + line range + routing mode) to the work surface so users can see where a snippet came from and jump back.
- Prefer a reusable scratch/work-file lifecycle over spawning only transient tabs.

### UX / lifecycle notes
- Work files should feel reusable per profile instead of one-shot.
- Running from source should not silently retarget existing execution/profile history semantics.
- Action placement should stay compact: editor/project-context entrypoints, not a long menu of near-duplicates.
- Wording must stay honest that this is still a lightweight execution workflow, not a semantic Spark IDE or notebook.

### New phase checklist
- [x] Re-audit current editor/action/workflow integration before editing
- [x] Implement core source-routing and work-surface model
- [x] Add IDE-native actions for selection/line/file flows
- [x] Wire reusable Livy work-file lifecycle and source-origin navigation
- [x] Strengthen source-to-result-to-reuse loop
- [x] Add tests for routing/context binding/no-regression
- [x] Update docs/metadata for the new IDE-native workflow
- [x] Verify full build/test/plugin checks

### Repair log
#### 2026-03-30 - Phase 4 design note
- Re-read the current action registrations, file editor provider, execution target binding, and console/work-surface lifecycle.
- Confirmed that the biggest platform gap is not more console features, but weak editor/action/native-file integration.
- Chosen direction: route new editor actions into one reusable Livy work surface with lightweight source-origin metadata, instead of starting a PSI/parser effort.

#### 2026-03-30 - Core routing and work-surface seam
- Added pure source-routing helpers for selection/current-line/whole-file flows so editor action behavior is testable without UI plumbing.
- Added reusable Livy work-surface request/origin metadata and work-file path helpers to route source snippets into one native execution surface.
- Switched the custom editor closer to file-backed document behavior so reusable work files behave more like normal JetBrains editors.
- Added source-aware navigation from the work file and result surface back to the originating source location.
- Preserved snapshot safety by preventing an already-bound work file from being silently rebound to a newer execution snapshot just because the action was invoked again.

#### 2026-03-30 - IDE-native actions and user-facing work-file model
- Added a compact set of entrypoints:
  - open source in a Livy work file
  - run selection or current line in Livy
  - run whole file in Livy
- Placed the new actions into editor/project context as one Livy popup group instead of scattering many separate commands.
- Updated file-type/editor/settings wording toward a reusable work-file model rather than a throwaway console tab.

#### 2026-03-30 - Docs, metadata, and shipped-path verification
- Updated `README.md` and `plugin.xml` to describe the work-file flow, editor/project popup entrypoints, and the still-lightweight/non-PSI scope honestly.
- Re-ran the full build/test/verify path after the platform-integration changes.

### Verification log
- 2026-03-30: static review completed for action registrations, `RunCodeViaLivyAction`, `LivyConsoleLauncher`, `LivyConsoleFileEditor`, `LivyConsolePanel`, and related editor integrations to define the platform-native seam.
- 2026-03-30: `./gradlew.bat test --console=plain` passed after adding source-routing helpers, reusable work-surface lifecycle, source-origin navigation, and the new IDE-native actions.
- 2026-03-30: `./gradlew.bat --gradle-user-home .gradle-user-home --no-daemon test buildPlugin verifyPlugin --stacktrace` passed after the platform-integration docs/metadata cleanup.

### Remaining risks
- Scratch/work-file APIs may require a small fallback path if this platform baseline behaves differently than expected.
- If source-routing grows too smart, it will drift toward parser/PSI territory and overcomplicate the phase.
- If action placement is too broad, the plugin will feel noisier rather than more native.
- The profile chooser still relies on a deprecated platform dialog helper; it is a low-priority tail, not the core blocker of this phase.

### Sanity-check: а не фигню ли я делаю? (before phase close)
- Это реально усиливает именно JetBrains-platform side? Да: the plugin now has context-menu entrypoints, reusable work files, source navigation, and file-backed editor behavior that fit the IDE workflow much better.
- Я не свалился ли обратно в “ещё одна фича консоли”? Нет: the main outcome is tighter editor/action/navigation integration, not another isolated console widget.
- Я не начал ли строить parser/PSI platform, хотя это вне scope? Нет: there is still no semantic language layer; routing stays intentionally shallow.
- Я не ломаю ли snapshot/profile/history model? Нет: full verification passed and the reusable work-file flow explicitly preserves bound targets once attached.
- UX стал более native или просто более шумным? More native: the action surface is still small, grouped, and tied to real editor/project contexts.

### Sanity-check: а не фигню ли я делаю? (after core routing/actions)
- Это реально усиливает именно JetBrains-platform side? Да: entrypoints now come from editor/project context and route into a reusable work-file surface instead of forcing users through one separate tool action.
- Я не свалился ли обратно в “ещё одна фича консоли”? Нет: the main changes are action routing, file lifecycle, and source navigation, not more isolated console chrome.
- Я не начал ли строить parser/PSI platform, хотя это вне scope? Нет: source routing stays on lightweight selection/line/file heuristics.
- Я не ломаю ли snapshot/profile/history model? Нет: work files keep their bound execution target once attached, and the tests still pass after the change.
- UX стал более native или просто более шумным? So far more native; the action surface is still compact and grouped.

## Phase 5 - Language-Aware Livy Work File
### Goal
- Make the reusable Livy work file feel more like a JetBrains-native editor surface by introducing lightweight language-aware modes and statement-aware execution ergonomics.
- Improve editor intelligence only where the platform already gives cheap leverage; do not turn this phase into a parser/PSI or semantic Spark tooling project.

### Scope
- Add a compact execution-kind/work-file-mode model for `SQL`, `PySpark / Python`, and fallback `Spark / Plain Text`.
- Make the bound work-file mode visible in the editor surface and use it to improve highlighting/title/header behavior where platform support exists.
- Add a narrow SQL statement-aware run heuristic for the work file without promising semantic parsing.
- Keep the existing profile/snapshot/history/source-origin model intact and make mode-aware restore/reuse honest about its limits.

### Non-goals
- No custom parser, PSI tree, inspections, completion engine, refactorings, or Spark semantic analysis.
- No notebook cell model or large redesign of the editor surface.
- No auth/storage work and no claim that the plugin now understands SQL/Python semantically.
- No heavy persistence layer for full execution-target snapshots across all IDE restarts.

### Proposed language-aware editor model
- Reuse the existing captured execution target as the single source of truth for work-file mode:
  - `kind == sql` -> `SQL`
  - `kind == python` or `kind == pyspark` -> `PySpark / Python`
  - everything else -> `Spark / Plain Text`
- Make the work-file mode part of the work-file identity so profile-bound files do not all collapse into one generic plain-text surface.
- Use platform file-type resolution opportunistically:
  - SQL highlighting if a SQL-capable file type is available in the running IDE
  - Python highlighting if a Python-capable file type is available
  - fallback to plain text otherwise
- Keep runtime binding snapshot-based for open work files; if a file is reopened without an attached in-memory target, rehydrate from persisted hints rather than inventing a new “smart” runtime model.

### Chosen JetBrains APIs / platform seams
- `FileTypeManager` for best-effort reuse of already-installed language support instead of creating a custom parser/highlighter.
- Existing custom file editor + editor toolbar/header for mode/profile/server context; keep the surface local to the Livy work file instead of starting a new editor framework.
- Existing action system for compact mode-aware run actions (`selection`, SQL `statement`, `file`) rather than adding many extra menus.
- Scratch/work-file path hints as a lightweight persistence seam for reopening the correct profile/mode identity without promising full remote session restore.

### Heuristics and limitations
- SQL “current statement” execution is intentionally heuristic:
  - selection wins when non-empty
  - otherwise split around the caret using semicolon boundaries and trimmed non-empty content
  - if the heuristic cannot isolate a useful statement, fall back to whole-file execution
- No SQL dialect awareness, no quote/comment parser, and no Python block intelligence in this phase.
- Mode-aware editor behavior is best-effort and depends on language/file-type support available in the host IDE.
- Reopened work files can preserve persisted profile/mode hints, but this phase still does not promise full remote session or full execution-snapshot resurrection after IDE restart.

### New phase checklist
- [x] Re-audit current work-file lifecycle and runtime seams before editing
- [x] Wire work-file mode into work-file identity and editor setup
- [x] Add mode-aware header/context and narrow run ergonomics
- [x] Add SQL statement heuristic and core regression tests
- [x] Update README/plugin metadata wording for language-aware limits
- [x] Verify full build/test/plugin checks

### Repair log
#### 2026-03-30 - Phase 5 design note
- Re-read the current work-file launcher, execution-target capture, custom file editor, source-routing helpers, and work-file panel before editing.
- Confirmed that the current weak spot is not missing “more console features”, but that the reusable work file still looks and behaves like generic plain text.
- Chosen direction: bind a lightweight `LivyWorkFileMode` to the captured execution kind, reuse platform file types where available, and add only a narrow SQL statement heuristic instead of a parser/PSI effort.

#### 2026-03-30 - Mode-aware work-file identity and editor wiring
- Added a compact `LivyWorkFileMode` model for `SQL`, `PySpark / Python`, and fallback `Spark / Plain Text`, including best-effort file-type reuse from the host IDE.
- Made work-file mode part of the work-file identity/path so reopened files can preserve profile + mode hints instead of collapsing back to one generic work surface.
- Wired the work-file editor to use mode-aware highlighting where available, surfaced explicit mode/highlighting text in the header, and updated the file-editor title to show profile + mode.
- Tightened local draft restore so drafts are keyed by `profile + kind` rather than only by profile, which avoids obviously wrong SQL-vs-Python restores.

#### 2026-03-30 - Statement-aware run ergonomics and regression coverage
- Reworked the work-file toolbar around `Run Current` and `Run File` so the editor surface exposes clearer execution semantics.
- Added a narrow SQL statement heuristic:
  selection wins; otherwise the current semicolon-delimited statement around the caret is used; otherwise the whole work file runs.
- Kept non-SQL behavior honest: no fake block intelligence for PySpark/plain Spark, only selection-or-file behavior.
- Added/updated tests for mode-aware draft restore, mode hints from work-file paths, capture-time kind override for reopened work files, and SQL statement selection behavior.

#### 2026-03-30 - Docs alignment and shipped-path verification
- Updated `README.md` and `plugin.xml` so the public description now matches the implemented mode-aware work-file behavior, best-effort host-IDE highlighting, and the documented SQL statement heuristic.
- Kept the wording explicit that this is still lightweight editor assistance, not a parser, notebook, completion engine, or semantic Spark IDE.
- Re-ran the full shipped-path verification gate after the language-aware docs/metadata cleanup.

### Verification log
- 2026-03-30: static review completed for `LivyConsoleLauncher`, `LivyExecutionTarget`, `LivyConsolePanel`, `LivyConsoleFileEditor`, `LivySourceRouting`, and the existing work-file actions to define the language-aware seam.
- 2026-03-30: `./gradlew.bat test --console=plain` passed after wiring mode-aware work-file identity, per-kind draft restore, and SQL statement routing.
- 2026-03-30: `./gradlew.bat --gradle-user-home .gradle-user-home --no-daemon test buildPlugin verifyPlugin --stacktrace` passed after README/plugin metadata alignment for the language-aware phase.

### Remaining risks
- If mode-aware behavior leaks into runtime capture via live settings, it will reintroduce the same drift bug in a new form.
- If SQL heuristics try to be too clever, the phase will drift into parser territory and become brittle.
- If mode-aware persistence stores too much target state, it will overcomplicate the code for a narrow editor improvement.
- If the copy is sloppy, users may infer semantic SQL/Python support that the plugin still does not implement.
- Highlighting quality still depends on the host IDE having the relevant SQL/Python file-type support installed; otherwise the work file intentionally falls back to plain text.
- Reopened work files preserve persisted profile/mode hints, not a full on-disk execution snapshot across IDE restarts.

### Sanity-check: а не фигню ли я делаю? (after mode/runtime/tests)
- Это реально усиливает JetBrains/editor side? Да: the work file now exposes a mode-aware editing surface, best-effort host-IDE highlighting, and clearer native run semantics.
- Я не начал ли строить parser/platform вместо узкого полезного улучшения? Нет: SQL support is still a documented semicolon heuristic, and no PSI/semantic layer was introduced.
- Я не сломал ли snapshot/profile/history/source-origin model? Нет по текущим тестам: mode stays tied to the captured target or persisted work-file hint, and drafts now align better with `profile + kind`.
- Editor стал нативнее или просто сложнее? More native so far: less plain-text generic behavior, more explicit editor context and run intent.
- Ограничения честно зафиксированы или я опять обещаю лишнее? Partly yes in the plan and UI hints; README/plugin metadata still need the final wording pass.

### Sanity-check: а не фигню ли я делаю? (before phase close)
- Это реально усиливает JetBrains/editor side? Да: the work file now behaves more like an editor-aware surface, not just a generic text box embedded in a custom tab.
- Я не начал ли строить parser/platform вместо узкого полезного улучшения? Нет: the phase stopped at file-type reuse, header/context wiring, and a narrow SQL heuristic.
- Я не сломал ли snapshot/profile/history/source-origin model? Нет: full `test + buildPlugin + verifyPlugin` passed with the existing routing/binding model still intact.
- Editor стал нативнее или просто сложнее? More native: clearer mode/title/context and run semantics without adding a heavy UI framework.
- Ограничения честно зафиксированы или я опять обещаю лишнее? Да: README, plugin metadata, and the plan now describe the exact heuristic and the still-missing semantic features explicitly.

### 2026-03-30 - Follow-up regression: work-file quick-run UX
- User testing surfaced a real workflow regression in the new editor popup path:
  `Run Selection or Line in Livy` could open a confusing dual-editor surface (`Text` + `Livy Work File`) and fail to make the quick-run behavior visible/reliable.
- Root cause direction: scratch-backed work files exposed an extra generic text editor surface, and the run request/autorun path still depended too much on whether the custom editor was already instantiated at open time.
- Chosen fix direction: simplify back to one user-facing work-file surface and add an explicit pending-request handoff so quick-run does not get lost during editor open lifecycle.

### 2026-03-30 - Follow-up regression: file-editor provider contract
- User testing then surfaced a platform exception from the custom editor provider:
  `HIDE_DEFAULT_EDITOR is supported only for DumbAware providers`.
- Root cause: `LivyConsoleFileEditorProvider` correctly requested `HIDE_DEFAULT_EDITOR`, but it did not implement the required `DumbAware` marker interface.
- Fix direction: make the provider explicitly `DumbAware` and rebuild a fresh test ZIP so the single-surface work-file path can be retested without this startup exception.

### 2026-03-31 - Follow-up polish: header density and work-file icon
- User testing then confirmed two low-risk UX rough edges remained in the now-working work-file surface:
  the header text was still too dense to scan, and the work-file tab icon was visually oversized.
- Fix direction: compress the visible header into short scannable lines, move verbose guidance into tooltips, and switch the work-file file-type icon to the smaller stripe-sized asset used elsewhere in the plugin.

### 2026-03-31 - Follow-up capability: safe manual session termination
- User testing identified a practical sessions-tool-window gap: there was still no manual way to terminate a session from the loaded list.
- Chosen scope stayed intentionally narrow:
  add `Terminate Managed Session…` only for plugin-managed sessions from the currently loaded server context, with explicit confirmation.
- Deliberately not implemented here:
  terminating arbitrary foreign sessions from the list. That would cross the current safe boundary and make the plugin look like a broader session-admin tool than it really is.
- Also tightened the sessions panel button-state so `Show Statements`, `View Logs`, and `Terminate Managed Session…` are enabled only when the current selection/context actually supports the action.

### 2026-03-31 - Follow-up capability: sessions auto-refresh control
- User testing then surfaced another practical sessions-panel gap:
  manual refresh is fine for debugging, but clumsy for day-to-day monitoring of session churn.
- Chosen scope stayed intentionally small and panel-local:
  add an `Auto-refresh` toggle plus interval-in-seconds control directly to the `Livy Sessions` panel, and persist those values in plugin settings.
- Implementation stays lightweight on purpose:
  Swing `Timer`, no overlapping refreshes, no modal error spam from background polling, and no attempt to turn the sessions panel into a real-time streaming dashboard.
- The current selected profile still defines what the next refresh loads; already-loaded actions remain bound to the loaded target as before.

### Verification log (follow-up)
- 2026-03-30: `./gradlew.bat test --console=plain` passed after removing the scratch-backed user surface from the launcher path and adding pending work-surface request delivery for reliable autorun.
- 2026-03-30: `./gradlew.bat test --console=plain` will be rerun after marking `LivyConsoleFileEditorProvider` as `DumbAware`, followed by a fresh labeled test build.
- 2026-03-31: `./gradlew.bat test --console=plain` passed after compacting the work-file header text and switching the file-type icon to the smaller stripe asset.
- 2026-03-31: `./gradlew.bat --gradle-user-home .gradle-user-home --no-daemon -PpluginVersionSuffix=test-20260331-polish buildPlugin --console=plain` produced a fresh manual-test ZIP for the polish pass.
- 2026-03-31: `./gradlew.bat test --console=plain` passed after adding `Terminate Managed Session…` and managed-session eligibility tests.
- 2026-03-31: `./gradlew.bat test --console=plain` passed after adding persisted sessions auto-refresh settings and timer-based panel wiring.
- 2026-03-31: `./gradlew.bat buildPlugin --console=plain` passed after the sessions auto-refresh panel changes.
- 2026-04-01: `./gradlew.bat test --console=plain` passed after fixing duplicate pending work-surface request application in the editor-popup run path and adding a one-shot consume regression test.

### Sanity-check: а не фигню ли я делаю? (after quick-run regression fix)
- Что именно я чиню: the main editor-popup quick-run path, not a cosmetic annoyance.
- Почему это реально приближает к цели: quick run becomes one coherent action again instead of a confusing dual-editor lifecycle.
- Не ушёл ли я в косметику вместо блокеров: нет, this is a primary execution entrypoint regression.
- Не создаю ли я лишнюю архитектурную сложность: no, the change removes scratch-specific UX complexity and adds only a small pending-request seam.
- Не вру ли я себе про “готовность”: not yet; a fresh test build should still be generated for user validation of this exact fix.

### Sanity-check: а не фигню ли я делаю? (after provider-contract fix)
- Что именно я чиню: a real IntelliJ platform contract violation in the custom work-file editor provider.
- Почему это реально приближает к цели: the single-surface editor path cannot be considered usable while it throws a provider exception.
- Не ушёл ли я в косметику вместо блокеров: нет, this is a hard runtime error in the main workflow.
- Не создаю ли я лишнюю архитектурную сложность: no, the fix is one marker interface to match the platform requirement.
- Не вру ли я себе про “готовность”: still no until the fresh test build is verified by the user.

### Sanity-check: а не фигню ли я делаю? (after header/icon polish)
- Что именно я чиню: readability and visual fit of the now-stable work-file surface.
- Почему это реально приближает к цели: it removes avoidable friction from the editor surface users now spend time in.
- Не ушёл ли я в косметику вместо блокеров: здесь уже low-risk polish, but only after the core run/editor regressions were repaired.
- Не создаю ли я лишнюю архитектурную сложность: no, only shorter visible text and a smaller icon asset.
- Не вру ли я себе про “готовность”: no; this is explicitly polish, not a claim that the broader product is “done”.

### Sanity-check: а не фигню ли я делаю? (after safe terminate action)
- Что именно я чиню: a real operational gap in the sessions list, but only within the plugin's existing managed-session boundary.
- Почему это реально приближает к цели: users can now clean up plugin-managed sessions without leaving the IDE or risking silent wrong-server drift.
- Не ушёл ли я в косметику вместо блокеров: нет, this is functional capability in an existing workflow.
- Не создаю ли я лишнюю архитектурную сложность: no, only a small managed-session eligibility helper, one button, and a confirm flow.
- Не вру ли я себе про “готовность”: no; this is intentionally not a generic “kill any Livy session” feature.

### Sanity-check: а не фигню ли я делаю? (after sessions auto-refresh control)
- Что именно я чиню: a practical monitoring/usability gap in the sessions tool window.
- Почему это реально приближает к цели: users no longer need to babysit the panel with manual refresh clicks just to watch session state drift.
- Не ушёл ли я в косметику вместо блокеров: нет, this is workflow capability, not decorative UI.
- Не создаю ли я лишнюю архитектурную сложность: no, just persisted interval settings and a guarded polling timer with no overlap.
- Не вру ли я себе про “готовность”: no; this is still lightweight polling, not a real-time session monitoring system.

### 2026-04-01 - Follow-up regression: duplicate run request
- User testing then surfaced another real bug in the editor-popup run path:
  selecting code and running it could show two execution tasks because the same work-surface request was being applied twice when a work file was opened.
- Root cause: the request was consumed once in `LivyConsoleFileEditor.init`, but `openLivyWorkSurface(...)` still unconditionally called `applyWorkSurfaceRequest(request)` again after `openFile(...)`.
- Fix direction: switch the launcher to “consume-and-apply if still pending” semantics so a request is applied exactly once whether the editor already existed or was just created.

### Sanity-check: а не фигню ли я делаю? (after duplicate-run fix)
- Что именно я чиню: a correctness bug in the main selection-run entrypoint, not just a progress-indicator quirk.
- Почему это реально приближает к цели: one user action now maps back to one execution request again.
- Не ушёл ли я в косметику вместо блокеров: нет, this is a core run-path regression.
- Не создаю ли я лишнюю архитектурную сложность: no, just removing one unconditional duplicate apply and locking it with a small regression test.
- Не вру ли я себе про “готовность”: no; this should still be manually rechecked on a fresh test build.

### 2026-04-01 - Release prep for Marketplace update
- Bumped the base plugin version from `1.4` to `1.5`.
- Rewrote `plugin.xml` Marketplace `change-notes` to match the actual shipped delta:
  profile-safe execution binding, reusable work files, local history, language-aware work-file editing, safer sessions workflow, and the explicit product boundary.
- Re-ran the release-grade local gate before handoff instead of treating the version bump as a cosmetic text edit.

### Verification log (release prep)
- 2026-04-01: `./gradlew.bat --gradle-user-home .gradle-user-home --no-daemon test buildPlugin verifyPlugin --stacktrace` passed after bumping the release version to `1.5` and updating Marketplace change notes.

### Sanity-check: а не фигню ли я делаю? (after release prep)
- Что именно я чиню: release metadata and artifact versioning for the next Marketplace upload.
- Почему это реально приближает к цели: the published version and Marketplace notes will now match the actual shipped behavior instead of lagging behind `1.4`.
- Не ушёл ли я в косметику вместо блокеров: нет, only after the feature/regression work was already completed and verified.
- Не создаю ли я лишнюю архитектурную сложность: no, just a base version bump and updated release notes.
- Не вру ли я себе про “готовность”: no; the release ZIP is backed by `test + buildPlugin + verifyPlugin`, not just by a version string change.

### 2026-04-01 - Follow-up maintenance: Marketplace deprecated API cleanup
- Marketplace compatibility verification for `1.5` came back compatible but noisy:
  one deprecated `Document.addDocumentListener(DocumentListener)` usage and one deprecated `Messages.showChooseDialog(...)` usage.
- Chosen scope stays deliberately narrow:
  remove exactly those deprecated platform seams without changing the plugin's product behavior or reopening the just-stabilized editor/runtime flows.
- Fix direction:
  replace the profile chooser with a tiny `DialogWrapper`-based selector and bind the work-file draft listener to a disposable-aware document listener registration.
- Regression coverage added where it was worth it:
  pure tests now lock the profile-selection option model and same-profile draft replacement behavior.
- Not included (because it was not worth the harness cost):
  a heavyweight UI test around the modal chooser/editor lifecycle. A first attempt pulled in flaky IDE test-harness behavior on this environment, so coverage was kept at the stable model/core layer instead.

### Sanity-check: а не фигню ли я делаю? (after deprecated API cleanup patch)
- Что именно я чиню: two concrete IntelliJ platform deprecation warnings reported by Marketplace verification.
- Почему это реально приближает к цели: it reduces release noise and future binary-compatibility risk without changing the product surface.
- Не ушёл ли я в косметику вместо блокеров: это уже maintenance, but it is directly tied to Marketplace verification and release quality.
- Не создаю ли я лишнюю архитектурную сложность: no, just a tiny modal chooser and disposable-bound listener lifecycle.
- Не вру ли я себе про “готовность”: в рамках этого follow-up нет; local `test + verifyPlugin` passed and the deprecated source usages are gone, but the Marketplace verifier will only reflect it after the next uploaded build.

### Verification log (deprecated API cleanup)
- 2026-04-01: `./gradlew.bat --gradle-user-home .gradle-user-home --no-daemon test verifyPlugin --console=plain` passed after replacing the deprecated profile chooser and disposable-unsafe document-listener registration.
- 2026-04-01: added stable regression tests for the profile-selection option model and same-profile draft replacement behavior.
- 2026-04-01: source scan no longer finds `Messages.showChooseDialog(...)`, `removeDocumentListener(...)`, or the old no-parent-disposable `addDocumentListener(draftListener)` usage in plugin sources.

### 2026-04-01 - Release prep for Marketplace verifier cleanup patch
- Chosen release shape: ship the deprecated-API cleanup as `1.5.1`, not as an unversioned rebuild of `1.5`.
- Reason: Marketplace compatibility verification is tied to uploaded artifacts; a distinct patch version makes the cleanup visible, auditable, and easy to reason about.
- Scope of `1.5.1` stays intentionally narrow:
  compatibility maintenance and regression coverage only, with no product-surface re-scope.

### Sanity-check: а не фигню ли я делаю? (before 1.5.1 build)
- Что именно я чиню: release versioning and handoff for the verifier-cleanup patch.
- Почему это реально приближает к цели: the next Marketplace upload will correspond to a clearly versioned artifact instead of “mystery 1.5 rebuild”.
- Не ушёл ли я в косметику вместо блокеров: нет, this is release hygiene directly attached to a verified compatibility warning cleanup.
- Не создаю ли я лишнюю архитектурную сложность: no, just a patch-version bump and tighter release notes.
- Не вру ли я себе про “готовность”: not yet; `test + buildPlugin + verifyPlugin` still need to pass for `1.5.1`.

### Verification log (1.5.1 release prep)
- 2026-04-01: `./gradlew.bat --gradle-user-home .gradle-user-home --no-daemon test buildPlugin verifyPlugin --stacktrace` passed after bumping the base version to `1.5.1` and rewriting Marketplace change notes for the verifier-cleanup patch.
- 2026-04-01: built Marketplace-ready artifact `build/distributions/livy_new-1.5.1.zip`.

### Sanity-check: а не фигню ли я делаю? (after 1.5.1 build)
- Что именно я чиню: the handoff artifact for the Marketplace verifier-cleanup patch release.
- Почему это реально приближает к цели: there is now one clear upload target with matching version, notes, tests, and local verifier pass.
- Не ушёл ли я в косметику вместо блокеров: нет, this directly closes the Marketplace follow-up loop.
- Не создаю ли я лишнюю архитектурную сложность: no, only release metadata and a fresh built artifact.
- Не вру ли я себе про “готовность”: для этого patch release нет; the local release-grade gate passed and the ZIP is ready for upload.
