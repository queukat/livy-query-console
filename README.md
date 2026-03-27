# Livy Query Console (JetBrains Plugin)

Run **Spark**, **PySpark**, and **Spark SQL** code on a remote cluster via **Apache Livy** right from JetBrains IDE.

> This plugin is an early public release. Feedback and PRs are welcome.

[![JetBrains Marketplace](https://img.shields.io/jetbrains/plugin/v/29406)](https://plugins.jetbrains.com/plugin/29406-livy-query-console)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/29406)](https://plugins.jetbrains.com/plugin/29406-livy-query-console)
[![Rating](https://img.shields.io/jetbrains/plugin/r/stars/29406)](https://plugins.jetbrains.com/plugin/29406-livy-query-console)

---

## Features

- **Livy Console editor** (virtual file): write code and execute it via Livy.
  - If you select text in the editor, **only the selection is executed**.
  - Otherwise the whole editor content is sent.
- **Result viewer**: each execution opens a new tab with:
  - **Raw**: full Livy JSON output
  - **Pretty**: human-readable view (including error details + traceback when available)
  - **Table**: renders Spark ASCII tables (e.g. output of `df.show()`) when detected
- **Tool Window: “Livy Sessions”**
  - List active sessions
  - Refresh
  - Open **Statements** and **Logs** dialogs for the selected session
- **Session logs dialog** with quick search (find next/prev)
- **Statements browser** for a session
- **Configurable connection & managed-session options** (Livy URL, kind, resources, TTL, conf, etc.)

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

1. Open **Tools → Livy → Livy: Open Query Console**
2. Configure **Settings → Tools → Livy**:
   - Livy Server URL (e.g. `http://localhost:8998`)
   - Session kind: `spark` / `pyspark` / `sql`
   - Optional resources (driver/executor memory, cores, etc.)
   - Reuse applies only to plugin-managed sessions created for the same server and matching configuration
3. Write a snippet and click **Run**

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
  - Validates wrapper, runs tests, builds plugin ZIP artifact
- **GitHub Release**: `.github/workflows/release.yml`
  - Runs on tag push `v*`
  - Builds plugin and attaches ZIP to GitHub Release automatically
- **Marketplace publish (manual)**: `.github/workflows/publish-marketplace.yml`
  - Run manually from GitHub Actions UI
  - Requires repository secret: `PUBLISH_TOKEN`
  - Optional signing secrets (if used): `CERTIFICATE_CHAIN`, `PRIVATE_KEY`, `PRIVATE_KEY_PASSWORD`

---

## Project structure (high level)

- `RunCodeViaLivyAction` — opens the Livy Console editor (virtual file)
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

 **MIT**
