# Article Research Plan

## Recommended article angle

Recommended angle: **keeping remote Spark exploration inside the JetBrains IDE by treating Livy as a lightweight execution back-end, with safe managed-session reuse as the engineering centerpiece.**

Why this angle currently fits:
- `README.md` and `src/main/resources/META-INF/plugin.xml` both describe the plugin as a quick exploration/debugging workflow inside the IDE, not as a notebook replacement or Spark platform.
- `src/main/kotlin/com/queukat/livy_new/SessionManager.kt` and `src/main/kotlin/com/queukat/livy_new/LivyManagedSessions.kt` already expose a concrete, defensible product idea: reuse only plugin-managed sessions for the same server and matching configuration fingerprint.

## Alternative title candidates

1. Keeping Remote Spark Exploration Inside the IDE with Apache Livy
2. Building a Livy Query Console for JetBrains IDEs
3. Safe Managed-Session Reuse for Remote Spark Execution in IntelliJ-Based IDEs

## Target audience

- Data engineers or Spark users who already have access to a Livy endpoint and want a tighter IDE loop than `curl`, Postman, or browser-based submission UIs.
- JetBrains plugin developers interested in how a narrow execution workflow can be made honest and useful without pretending to be a notebook.
- Technical readers who care about product boundaries: what is implemented, what is explicitly out of scope, and why.

## Most important workflow pain

The likely pain is not "Spark is hard"; it is the repeated context switch around **small remote experiments**:
- select code in the IDE;
- copy or retype it into an external HTTP client or shell flow;
- remember which Livy server/session was used;
- inspect raw results/logs separately from source;
- repeat after each small adjustment.

## Strongest differentiator

Current strongest differentiator: **safe, bounded reuse of plugin-managed Livy sessions tied to server identity and configuration fingerprinting** rather than naive "reuse any idle session" behavior.

Why this is stronger than generic "run code from IDE":
- opening a work file and sending snippets is useful but not unique by itself;
- the repository appears to spend real design effort on avoiding accidental session reuse across different settings/server targets;
- this is specific enough to anchor an engineering article instead of a vague product overview.

## Likely weak or misleading areas

- Overstating the console as notebook-like. The repo repeatedly says this is a lightweight execution workflow, not notebook persistence or semantic tooling.
- Claiming rich language intelligence. Current evidence points to host-IDE highlighting reuse plus a narrow SQL statement heuristic, not full Spark-aware parsing/completion.
- Claiming enterprise auth/security maturity. `README.md` and `plugin.xml` explicitly say advanced auth flows and secure credential storage are not implemented yet.
- Claiming robust multi-environment support too broadly. The repo appears to have multiple profiles, but article claims must stay precise about snapshot binding and current follow-up behavior.
- Treating marketplace downloads as proof of adoption. Not included because that would not demonstrate real production use.

## Not included (because not yet evidenced)

- Notebook cell semantics
- Spark-specific code completion, inspections, refactorings, or parser-level language support
- Kerberos, OAuth, bearer token headers, cookies, or secure credential storage
- Multi-cluster orchestration or environment governance features
- Any adoption or ROI claims beyond what repository artifacts directly show

## Initial evidence priorities

1. Reconstruct the concrete user flow from action entrypoints to console execution to result/log/session inspection.
2. Verify how managed-session reuse is keyed and pruned.
3. Verify how profiles/settings are stored and how execution targets are captured.
4. Verify limitations from code/tests rather than README wording alone.
5. Check tests and CI for confidence level and product maturity signals.

## А не фигню ли я делаю?

- Real user workflow focus: yes, the plan is anchored on small remote Spark experiments from inside the IDE.
- IDE productivity tool, not platform: yes, the angle explicitly avoids turning this into a general Spark platform story.
- Overclaiming security/auth or IntelliJ internals: no, current wording keeps auth/support claims conservative and avoids claiming deep IDE semantics.
- Is managed-session reuse really strongest: tentatively yes, but this will be re-evaluated after reading the execution-target and profile code.
- Enough material for one focused article: likely yes, if the code confirms the end-to-end workflow and the safety rationale behind reuse.
