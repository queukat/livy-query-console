# Editorial Pack

## Title ideas

1. Keeping Remote Spark Exploration Inside the IDE with Apache Livy
2. Building a Livy Query Console for JetBrains IDEs
3. Safe Managed-Session Reuse for Remote Spark Execution in IntelliJ-Based IDEs
4. From Editor Snippet to Livy Statement: A Lightweight Spark Loop in JetBrains IDEs
5. A JetBrains Work File for Livy: Remote Spark Iteration Without Leaving the IDE

## Short abstract 1

This article looks at a JetBrains plugin that runs Spark, PySpark, and Spark SQL snippets against Apache Livy without pretending to be a notebook or a full Spark platform. The interesting part is not just the IDE work file, but the way the plugin captures a profile snapshot and reuses only plugin-managed sessions whose server and runtime configuration match.

## Short abstract 2

If your Spark experiments already live in a JetBrains IDE, bouncing out to `curl`, Postman, or browser-based Livy pages is mostly context-switch tax. This piece walks through a Livy query console plugin whose value comes from keeping that remote execution loop in the IDE while staying honest about limits like direct-connect auth, shallow drill-down, and language-aware rather than language-smart editing.

## JetBrains-oriented pitch

A good JetBrains-plugin story does not need to claim deep code intelligence to be interesting. This one is about designing an honest custom editor surface around a remote execution API: profile snapshot binding, source-aware snippet routing, result/log/statement inspection, and conservative managed-session reuse that avoids unsafe cross-target leakage.

## DZone-oriented pitch

This is a practical remote-Spark workflow story, not a platform announcement. The article can show how a small IntelliJ plugin wraps the Livy REST loop, keeps exploration inside the IDE, and uses server-plus-fingerprint matching to reuse only safe managed sessions instead of gambling on any idle session returned by Livy.

## What this article is not

- Not a notebook replacement story.  
  Evidence: notebook persistence/cells are explicitly out of scope in `README.md` and `plugin.xml`.
- Not a "Spark intelligence in IntelliJ" story.  
  Evidence: only host-IDE file-type reuse and a narrow SQL heuristic are implemented (`LivyWorkFileMode.kt`, `LivySourceRouting.kt`).
- Not an enterprise auth/platform story.  
  Evidence: advanced auth and secure credential storage are explicitly unimplemented (`LivyPluginConfigurable.kt`, `plugin.xml`, `README.md`).
- Not an adoption/popularity story.  
  Evidence: repository artifacts show functionality, tests, and workflows, but do not justify production adoption claims.

## Editorial emphasis to keep

- Lead with user workflow pain, not plugin feature count.
- Use managed-session reuse as the technical spine.
- Keep boundary language explicit and early.
- Treat screenshots as proof of real UI, not marketing decoration (`LivyUiScreenshotTest.kt`, `docs/screenshots/README.md`).

## А не фигню ли я делаю?

- Real user workflow focus: yes, even the pitches stay anchored to the user’s editor-to-Livy loop.
- IDE productivity tool, not platform: yes, all title/pitch variants stay deliberately narrow.
- Overclaiming security/auth or IntelliJ internals: no, the pack explicitly rules those stories out.
- Is managed-session reuse really strongest: yes, it remains the technical spine across the abstracts and pitches.
- Enough material for one focused article: yes, the editorial framing is now stable.
