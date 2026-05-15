# Livy Query Console (JetBrains Plugin)

<!-- public-repo-status -->
> Status: Active early-release JetBrains Marketplace plugin. Issues are open for reproducible bugs and focused feature requests.

Run **Spark**, **PySpark**, and **Spark SQL** code on a remote cluster via **Apache Livy** right from JetBrains IDE.

> This plugin is an early public release. Feedback and PRs are welcome.

[![Install Plugin](https://img.shields.io/badge/Install%20Plugin-JetBrains%20Marketplace-000000?logo=jetbrains)](https://plugins.jetbrains.com/plugin/29406-livy-query-console)
[![JetBrains Marketplace](https://img.shields.io/jetbrains/plugin/v/29406)](https://plugins.jetbrains.com/plugin/29406-livy-query-console)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/29406)](https://plugins.jetbrains.com/plugin/29406-livy-query-console)
[![Rating](https://img.shields.io/jetbrains/plugin/r/stars/29406)](https://plugins.jetbrains.com/plugin/29406-livy-query-console)
![Local SonarQube Quality Gate](https://img.shields.io/badge/Local%20SonarQube-Quality%20Gate%20OK-brightgreen)
![Coverage](https://img.shields.io/badge/Coverage-81.0%25-brightgreen)
![Issues](https://img.shields.io/badge/Issues-0-brightgreen)
![Duplications](https://img.shields.io/badge/Duplications-0.0%25-brightgreen)

<details>
<summary><strong>Local SonarQube quality snapshot</strong> (checked 2026-05-16)</summary>

| Metric | Value |
| --- | --- |
| Quality Gate | OK |
| Overall coverage | 81.0% |
| New-code coverage | 100.0% (gate threshold: 80%) |
| Overall duplicated lines | 0.0% |
| New-code duplicated lines | 0.0% (gate threshold: 3%) |
| Bugs | 0 |
| Vulnerabilities | 0 |
| Code smells | 0 |
| New violations | 0 |

These values are a checked local SonarQube API snapshot, not live cloud badges.
</details>

---

## Features

- **Livy work file**: open a reusable Livy editor surface and route source snippets into it.
  - Use editor/project popup actions to **open source in a Livy work file**
  - Or run the **current selection or line** / **whole file** in Livy without leaving the IDE workflow
  - The work file follows the bound execution kind (`sql`, `pyspark` / `python`, or fallback plain Spark text) and uses host-IDE highlighting where that file type support is available
  - Inside a SQL-bound work file, **Run Current** uses a documented semicolon-based statement heuristic around the caret; **Run File** always runs the full work file
- **Result viewer**: each execution opens a new tab with:
  - **Raw**: full Livy JSON output
  - **Pretty**: human-readable view (including error details + traceback when available)
  - **Table**: renders Spark ASCII tables (e.g. output of `df.show()`) when detected
- **Tool Window: “Livy Sessions”**
  - List active sessions
  - Refresh manually or enable auto-refresh
  - Choose visible session columns
  - Open **Statements** and **Logs** dialogs for the selected session
  - Terminate plugin-managed sessions only
- **Local work-file history & last-draft restore**
  - Recent snippets are stored locally per profile with bounded retention
  - Reuse, replace, rerun, or copy a recent snippet from the work-file history dialog
- **Session logs dialog** with quick search (find next/prev)
- **Statements browser** for a session with deeper statement details and "open in work file" reuse
- **Multiple connection profiles** with per-profile Livy URL, session kind, resource settings, and managed-session options
- **Browser sign-in for SSO/OAuth2-proxy protected Livy** with per-profile session-cookie reuse via the IDE Password Safe and auth-required prompts

---

## Requirements

- JetBrains IDE based on IntelliJ Platform (IDEA, DataGrip, PyCharm, etc.)
- **Apache Livy** server on HTTP(S)
- Java 17 (recommended; aligns with modern JetBrains Runtime)

> Compatibility: the plugin targets **since-build 231** (IntelliJ IDEA 2023.1+).  
> Adjust `since-build` in `plugin.xml` if you need another baseline.

---

## Installation

### From Marketplace
1. Open **Settings / Preferences → Plugins**
2. Search for **Livy Query Console**  
   or open directly: https://plugins.jetbrains.com/plugin/29406-livy-query-console/
3. Install and restart the IDE

### From ZIP (local build)
1. Download the plugin ZIP from **Releases**
2. **Settings / Preferences → Plugins → ⚙ → Install Plugin from Disk…**
3. Select the ZIP and restart

---

## Quick Start

1. Open **Tools → Livy → Livy: Open Work File**
2. Configure **Settings → Tools → Livy**:
   - Create one or more named connection profiles
   - Set each profile's Livy Server URL (e.g. `http://localhost:8998`)
   - Set session kind: `spark` / `pyspark` / `sql`
   - Optional resources (driver/executor memory, cores, etc.)
   - Choose which profile is the default for new work-file/sessions loads
   - Reuse applies only to plugin-managed sessions created for the same server and matching configuration
3. Choose the profile to bind the work file or run action to
4. Use the editor/project **Livy** popup group to open source in a work file or run the current selection/line/file directly
5. In the work file, use **Run Current** for selection-or-statement execution and **Run File** for a full resend
6. Reuse recent snippets from the work-file **History** action or let an empty work file restore the last local draft for that profile and execution kind
7. If the server is behind SSO / oauth2-proxy, use **Settings → Tools → Livy → Authenticate via Browser…** or accept the sign-in prompt when the plugin detects an auth-required response

## Supported Boundary (Current Scope)

- The plugin now supports **multiple named connection profiles** in IDE settings.
- Each new Livy work file or sessions refresh binds itself to the **selected profile snapshot** captured when that flow starts.
- If you later edit settings or switch the active/default profile, **open a new work file or refresh sessions again** to use the new target. Existing loaded contexts do not silently switch.
- Optional local history stores recent snippets and the last draft **locally in plain text** with bounded retention. It is meant for quick rerun/reuse, not notebook or remote-session restore.
- Managed-session reuse/cleanup applies only to **plugin-managed sessions on the same server with matching configuration**.
- IDE-native entrypoints cover opening source in a Livy work file and running the current selection or line / whole file. This is still a lightweight execution workflow, not semantic Spark language tooling.
- The work file is now **language-aware, not language-smart**: it reuses SQL/Python/plain-text editor support when the host IDE already has it, but it does not add its own parser, completion, inspections, or refactorings.
- SQL work files support a narrow **semicolon-delimited current-statement heuristic** for `Run Current`. If that heuristic is ambiguous, the plugin falls back to running the whole work file.
- Best fit today: direct or SSO-protected Livy HTTP(S) endpoints where browser sign-in can establish a reusable session cookie.
- Not stored by local history: auth material, credentials, cookies, headers, or full remote session state.
- Browser-based sign-in can now capture per-profile session cookies and store them in the IDE Password Safe for reuse by the Livy client.
- Not included yet: manual auth headers/tokens, Kerberos, generic OAuth token management outside browser-session cookies, SQL dialect intelligence, Python block analysis, or notebook cells.

### Example: Scala / Spark
```scala
val df = spark.range(10)
df.show(10, false)
```

### Example: Spark SQL
```scala
spark.sql("select 1 as a, 2 as b").show()
```

### Example: PySpark
```python
from pyspark.sql.functions import *
spark.range(10).show()
```

---

## Notes on “Table” output

The **Table** tab is shown only when the output contains an ASCII table (for example, from `df.show()`).

If you run an expression like `spark.range(100)` without printing, Spark returns a Dataset description (schema), not rows.
In that case the plugin will show a helpful message instead of an empty table.

---

## SSL / Certificates (PKIX errors)

If you see an error like:

> `PKIX path building failed ... unable to find valid certification path`

your Livy endpoint uses a certificate that the JetBrains Runtime (JBR) does not trust (self-signed / corporate CA / missing chain).

Recommended fixes:
- Install a proper certificate chain on the proxy/server, **or**
- Add your corporate CA / cert chain to the **JetBrains Runtime truststore** used by the IDE, **or**
- Provide a custom truststore via IDE VM options (`-Djavax.net.ssl.trustStore=...`)

---

## Development

### Build & run in a sandbox IDE
```bash
./gradlew runIde
```

### Build plugin distribution ZIP
```bash
./gradlew buildPlugin
```

The resulting ZIP is usually in:
`build/distributions/`

### Publish to JetBrains Marketplace locally
Standard publish command:
```powershell
.\gradlew publishPlugin
```

Safer release-grade publish in one go:
```powershell
.\gradlew test buildPlugin verifyPlugin publishPlugin
```

The Gradle publish task reads the Marketplace token from the standard Gradle property/env used by this project
(`ORG_GRADLE_PROJECT_intellijPlatformPublishingToken` or `PUBLISH_TOKEN` for local publishing).

Searchable-options generation is skipped by default because it pulls platform-specific JetBrains Runtime artifacts and is fragile under dependency verification.
If you intentionally need to rebuild searchable options on a non-Windows host, run the build with `-PlivyBuildSearchableOptions=true`.

### Correct already-published Marketplace notes
Marketplace release notes are immutable for an uploaded version. If the ZIP was good but `change-notes` were wrong, use the same version only by deleting the Marketplace update first and uploading the corrected ZIP again:
```powershell
$token = $env:ORG_GRADLE_PROJECT_intellijPlatformPublishingToken
if (-not $token) { $token = $env:PUBLISH_TOKEN_PLUGIN }
if (-not $token) { $token = $env:PUBLISH_TOKEN }
$headers = @{ Authorization = "Bearer $token" }
$version = "1.5.5"

$updates = Invoke-RestMethod -Headers $headers -Uri "https://plugins.jetbrains.com/api/plugins/updates?xmlId=com.queukat.livy-new&version=$version&family=intellij"
$updates | Select-Object id,version,channel,pluginId,hidden

.\gradlew buildPlugin verifyPlugin --no-configuration-cache --stacktrace
Invoke-RestMethod -Method Delete -Headers $headers -Uri "https://plugins.jetbrains.com/api/updates/$($updates.id)"
.\gradlew publishPlugin --no-configuration-cache --stacktrace

Invoke-RestMethod -Headers $headers -Uri "https://plugins.jetbrains.com/api/plugins/updates?xmlId=com.queukat.livy-new&version=$version&family=intellij" |
  Select-Object id,version,channel,pluginId,hidden,notes
```

### Useful Gradle tasks
- `verifyPlugin`
- `buildPlugin`
- `generateScreenshots`
- `runIde`

### Generate plugin screenshots
```bash
./gradlew generateScreenshots
```

Generated PNG files are written to:
`docs/screenshots/`

---

## CI / CD

- **CI**: `.github/workflows/ci.yml`
  - Runs on push/PR
  - Validates wrapper, runs tests, builds plugin ZIP artifact, runs `verifyPlugin`
- **Marketplace publish (manual)**: `.github/workflows/publish-marketplace.yml`
  - Run manually from GitHub Actions UI
  - Re-runs tests/build/`verifyPlugin` before `publishPlugin`
  - Accepts repository secret: `ORG_GRADLE_PROJECT_intellijPlatformPublishingToken` or `PUBLISH_TOKEN`
  - Optional signing secrets (if used): `CERTIFICATE_CHAIN`, `PRIVATE_KEY`, `PRIVATE_KEY_PASSWORD`
- **Local publish (recommended if you do not use GitHub Releases)**: `.\gradlew publishPlugin`
  - Uses `ORG_GRADLE_PROJECT_intellijPlatformPublishingToken` or `PUBLISH_TOKEN`
  - For a release-grade local push, prefer `.\gradlew test buildPlugin verifyPlugin publishPlugin`

---

## Project structure (high level)

- `RunCodeViaLivyAction` / `RunSelectionOrLineInLivyAction` / `RunFileInLivyAction` — IDE entrypoints into the Livy work surface
- `LivyConsoleFileEditor*` — custom editor + UI
- `LivySessionsWindowFactory` + `LivySessionsPanel` — tool window
- `LivyClient` — Livy REST API client (OkHttp)
- `SessionManager` — safely reuse/create plugin-managed sessions according to settings
- `LivyPluginSettings` / `LivyPluginConfigurable` — persistent settings + UI

---

## Contributing

PRs are welcome. If you plan a bigger change, open an issue first to align on the approach.

---

## License

<!-- commercial-license-policy -->
This project is licensed for non-commercial use under the [PolyForm Noncommercial License 1.0.0](https://polyformproject.org/licenses/noncommercial/1.0.0.txt).
Commercial use, resale, paid distribution, marketplace publication, SaaS hosting, or bundling into a paid product requires separate written permission from the author.
Project names, logos, package identifiers, store listings, screenshots, and other branding assets are not licensed for use in forks or redistributed builds.
