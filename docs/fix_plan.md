# Fix Plan

## Context
- Repository: current workspace JetBrains plugin project for Livy.
- Audit file: not found in repository at the start of this task.
- Working baseline: confirmed audit findings were re-checked against the current code before changes.
- Goal: fix confirmed production and high-ROI issues without broad unrelated refactoring.

## Audit items review
| ID | Issue | Severity | Audit says | Current status | Notes |
|----|-------|----------|------------|----------------|-------|
| A1 | Unsafe session reuse without ownership/provenance checks | Critical | Sessions can be reused by `idle` state only | fixed | Reuse is restricted to plugin-managed sessions tracked in persistent state |
| A2 | Unsafe session delete without ownership/provenance checks | Critical | Oldest idle session can be killed without ownership validation | fixed | Auto-delete now targets managed idle sessions only |
| A3 | Wrong session matching / no compatibility checks | Critical | Reuse ignores kind/config compatibility | fixed | Session fingerprint matching was added for reuse decisions |
| A4 | Raw `Thread`, infinite polling, no timeout/cancel/dispose-safe lifecycle | Critical | Console execution is not lifecycle-safe | fixed | Console execution moved to cancellable background task flow with bounded waiting and dispose-safe cancellation |
| A5 | Stubbed/misleading completion UI | High | Completion is advertised but not wired | fixed | Completion surface was removed from the product-facing UI/metadata instead of leaving a broken action |
| A6 | Misleading editor popup text (`Run via Livy…`) | High | Surface implies execution but only opens console | fixed | Action wording now reflects the actual behavior |
| A7 | Dead code / half-built surface (`ChooseSessionDialog`, `ChooseColumnsDialog`, `NetworkService`) | Medium | Unused code hurts supportability | fixed | Unused half-built files were removed after re-checking references |
| A8 | Sessions list capped to first page only | High | Session view/logic sees only first 10 sessions | fixed | Added paged `getAllSessions()` and switched critical callers to it |
| A9 | Missing or weak verification/tests | High | No real tests/smoke confidence | fixed | Added targeted tests and completed `test`, `buildPlugin`, and `verifyPlugin` |
| A10 | API/platform smell around runtime `LightVirtualFile` and plain-text editor surface | Medium | Runtime/editor integration is weak | partially fixed | Editor provider matching no longer relies on `fileType.name`; remaining `LightVirtualFile` usage is low-risk and documented |
| A11 | Separate audit markdown file | Low | Needed as input artifact | stale / not reproducible | File was absent; replaced by this living plan document |

## Execution plan
### Phase 1 — Critical
- [x] Implement managed-session provenance tracking and safe reuse rules
- [x] Restrict session deletion to plugin-managed idle sessions only
- [x] Add compatibility matching for reusable sessions
- [x] Replace raw console execution threads with cancellable background task flow
- [x] Add bounded waiting, cancellation checks, and dispose-safe shutdown for console execution
- [x] Remove first-page-only session management logic for critical paths

### Phase 2 — High
- [x] Remove or hide misleading completion surface
- [x] Fix misleading popup/menu wording to reflect actual behavior
- [x] Make settings/session messaging honest about managed-session semantics
- [x] Add at least targeted tests or smoke-verifiable logic for new session policy

### Phase 3 — Medium / Low
- [x] Remove unused dead code that is no longer part of the product surface
- [x] Revisit low-risk platform/API cleanup if it remains worthwhile

## Repair log
### Step 0
- Status: done
- Problem: No standalone audit markdown was present in the repository.
- Fix: Created this living plan and re-validated audit findings against the current codebase before making changes.
- Files: `docs/fix_plan.md`
- Verification: Repository scan confirmed no existing audit markdown file.
- Notes: This file is now the canonical execution log for the repair work.

### Step 1
- Status: done
- Problem: Session reuse/delete were unsafe and based on arbitrary server sessions; session matching ignored compatibility; critical paths only saw the first page of sessions.
- Fix: Added persistent managed-session tracking, config fingerprinting, safe reuse rules, safe auto-delete rules, and paged session loading for critical callers.
- Files: `src/main/kotlin/com/queukat/livy_new/LivyManagedSessions.kt`, `src/main/kotlin/com/queukat/livy_new/LivySessionSpec.kt`, `src/main/kotlin/com/queukat/livy_new/LivyPluginSettings.kt`, `src/main/kotlin/com/queukat/livy_new/LivyClient.kt`, `src/main/kotlin/com/queukat/livy_new/SessionManager.kt`, `src/main/kotlin/com/queukat/livy_new/bottompanel/LivySessionsPanel.kt`
- Verification: Static code review of the new managed-session path; confirmed `SessionManager` now uses managed-session IDs plus fingerprint matching and `getAllSessions()` in critical paths.
- Notes: Build verification is still pending because Gradle execution is currently environment-limited. Next step is execution lifecycle repair.

### Step 2
- Status: done
- Problem: Console execution used raw threads, infinite polling, and had no dispose-safe cancellation path.
- Fix: Replaced raw execution threads with `Task.Backgroundable`, added polling timeout and cancellation checks, wired cancel through `ProgressIndicator`, and ensured editor dispose cancels in-flight work.
- Files: `src/main/kotlin/com/queukat/livy_new/editor/ui/LivyConsolePanel.kt`, `src/main/kotlin/com/queukat/livy_new/editor/LivyConsoleFileEditor.kt`
- Verification: Static code review confirmed removal of raw `Thread` execution from the console path and the presence of bounded statement waiting plus `disposePanel()` cancellation.
- Notes: Session creation still waits via polling in `SessionManager`, but now participates in task cancellation and is bounded.

### Step 3
- Status: done
- Problem: Product surface still advertised incomplete behavior, and dead code kept half-built flows in the repository.
- Fix: Removed the visible completion surface and its product claims, corrected popup wording, aligned settings copy with managed-session semantics, and deleted unused dead-code files/back-end helpers that no longer had live references.
- Files: `src/main/resources/META-INF/plugin.xml`, `README.md`, `src/main/kotlin/com/queukat/livy_new/LivyPluginConfigurable.kt`, `src/main/kotlin/com/queukat/livy_new/LivyClient.kt`, `src/main/kotlin/com/queukat/livy_new/LivyModels.kt`, deleted `ChooseSessionDialog.kt`, `ChooseColumnsDialog.kt`, `NetworkService.kt`
- Verification: Repository-wide reference scan after deletion confirmed the removed files and completion backend were not used by the live product surface.
- Notes: The remaining low-risk platform/API cleanup is optional and non-blocking compared with test/verification work.

### Step 4
- Status: done
- Problem: Verification was weak, Gradle verification initially had sandbox-related instability, and the file-editor provider still had a brittle file-type match.
- Fix: Added targeted tests for managed-session and session-spec logic, redirected plugin verifier caches into the workspace, switched Kotlin compilation to in-process mode for reliable local verification, re-ran Gradle verification sequentially, tightened file-editor acceptance to match by console file extension instead of `fileType.name`, and ignored the local verification-only Gradle home cache.
- Files: `src/test/kotlin/com/queukat/livy_new/LivyManagedSessionsTest.kt`, `src/test/kotlin/com/queukat/livy_new/LivySessionSpecFactoryTest.kt`, `build.gradle.kts`, `gradle.properties`, `.gitignore`, `src/main/kotlin/com/queukat/livy_new/editor/LivyConsoleFileEditorProvider.kt`
- Verification: `./gradlew.bat --gradle-user-home .gradle-user-home --no-daemon test buildPlugin verifyPlugin --stacktrace` completed successfully. Repository-wide scans also confirmed the misleading completion/dead-code surface was gone.
- Notes: An earlier `instrumentCode` failure turned out to be a false signal caused by running multiple Gradle builds in parallel against the same `build/` directory. Sequential verification passed cleanly.

### Step 5
- Status: done
- Problem: The packaged plugin did not have clear English release notes for end users, so the latest improvements were not obvious in Marketplace/ZIP metadata.
- Fix: Rewrote `plugin.xml` change notes as user-facing English “What’s new” release notes focused on safer session handling, better execution reliability, improved session visibility, and clearer UI wording.
- Files: `src/main/resources/META-INF/plugin.xml`
- Verification: Static review of the plugin metadata confirmed the new release notes are accurate and aligned with the implemented changes.
- Notes: This is a packaging/documentation improvement only; no runtime behavior changed in this step.

### Step 6
- Status: done
- Problem: The project had no reproducible way to generate user-facing screenshots, and the version number still pointed to `1.3`, which had already been published.
- Fix: Added a dedicated `generateScreenshots` Gradle task, screenshot tests that render real plugin UI panels into `docs/screenshots/`, a test-only in-memory preferences factory to make that task work in restricted environments, documentation for the screenshot workflow, and bumped the plugin version to `1.4` with matching release-notes heading.
- Files: `build.gradle.kts`, `README.md`, `src/test/kotlin/com/queukat/livy_new/LivyUiScreenshotTest.kt`, `src/test/kotlin/com/queukat/livy_new/testsupport/InMemoryPreferencesFactory.kt`, `src/main/kotlin/com/queukat/livy_new/editor/ui/LivyConsolePanel.kt`, `src/main/kotlin/com/queukat/livy_new/bottompanel/LivySessionsPanel.kt`, `src/main/resources/META-INF/plugin.xml`, `docs/screenshots/README.md`, generated `docs/screenshots/*.png`
- Verification: `./gradlew.bat --gradle-user-home .gradle-user-home --no-daemon test generateScreenshots buildPlugin verifyPlugin --stacktrace` completed successfully and produced `settings.png`, `console.png`, and `sessions.png`.
- Notes: Screenshot generation is deterministic and does not require manual clicking; it uses real Swing/IntelliJ UI components rendered from tests.

## Verification summary
- [x] tests
- [x] buildPlugin
- [x] verifyPlugin
- [x] generateScreenshots
- [x] manual/smoke checks

## Remaining issues / not fixed
- The console still uses a runtime `LightVirtualFile`; this is a low-risk residual platform smell, but the critical/editor-matching issue around it has been reduced.
- The console editor remains intentionally plain-text; this is consistent with the current product scope, but richer PSI/language integration would require a larger product decision rather than a safe bug fix.
- Gradle verification now passes locally; the generated local `.gradle-user-home/` cache is a workspace-only verification artifact and is now ignored.
