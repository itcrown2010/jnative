# Documentation Migration Report

**Date:** 2026-10-13  
**Scope:** English-first public documentation for GitHub internationalization.

---

## Files Created

| Path | Purpose |
|------|---------|
| `README.zh-CN.md` | Chinese landing (supplementary) |
| `docs/en/README.md` | English docs index |
| `docs/en/getting-started.md` | Clone → build → run |
| `docs/en/architecture.md` | Current vs planned architecture |
| `docs/en/language.md` | Supported language surface |
| `docs/en/compiler.md` | CLI / pipeline |
| `docs/en/runtime.md` | GC, exceptions, threading, FFI, I/O |
| `docs/en/roadmap.md` | Completed / current / next / long-term |
| `docs/en/I18N_INVENTORY.md` | Pre-migration audit |
| `docs/en/DOCUMENTATION_MIGRATION_REPORT.md` | This report |
| `docs/zh-CN/README.md` | Chinese navigation hub |
| `CONTRIBUTING.zh-CN.md` | Preserved Chinese contributing draft |
| `CODE_OF_CONDUCT.md` | Contributor Covenant–style CoC |
| `SECURITY.md` | Vulnerability reporting policy |
| `.github/ISSUE_TEMPLATE/bug_report.md` | Bug template |
| `.github/ISSUE_TEMPLATE/feature_request.md` | Feature template |
| `.github/ISSUE_TEMPLATE/config.yml` | Template config + doc links |
| `.github/pull_request_template.md` | PR checklist |

---

## Files Updated

| Path | Change |
|------|--------|
| `README.md` | Rewritten as English-first international landing page |
| `CONTRIBUTING.md` | English-first contributing guide (legal + workflow) |
| `docs/developer/README.md` | Points to `docs/en/` as English authority |
| `examples/Hello.java` | Restored to e2e-expected `Hello JNative` output (was drifting) |

---

## Chinese Documentation Preserved

- `README.zh-CN.md` (new Chinese summary; does not delete tutorial corpus)
- `CONTRIBUTING.zh-CN.md` (copy of prior Chinese CONTRIBUTING draft)
- `docs/tutorial/**` (unchanged Chinese tutorial)
- `docs/developer/FACT_INVENTORY.md` and related ZH maintainer docs
- `website/` Chinese VitePress site (unchanged this pass; only linked)

---

## English Documentation Added

Public English path: `docs/en/` + English `README.md` + root `CONTRIBUTING.md` / `SECURITY.md` / `CODE_OF_CONDUCT.md` + GitHub templates.

---

## Code Examples Verified

| Example | Command | Result |
|---------|---------|--------|
| `examples/guide-hello/src/Main.jn` | `java -jar target/jnative.jar … -o /tmp/jn-Hello -O0 && /tmp/jn-Hello` | `Hello, JNative!` |
| `examples/Hello.java` (after fix) | compile + run | `Hello JNative` |
| `jnative doctor` | CLI | Compiler/LLVM/clang/Linker/Runtime/Package cache ✓ |

Getting Started build path (`mvn -q -DskipTests package`) verified in this session.

---

## Tests

| Suite | Result |
|-------|--------|
| `ExceptionEndToEndTest` | Pass (25 tests) |
| `CallableEndToEndTest` | Pass (13 tests) |
| `EndToEndTest#examplesDirectory` | Pass after `Hello.java` alignment |
| Full `mvn test` | **Not fully run** in this pass (suite is large); focused e2e above executed |

Earlier `EndToEndTest.examplesDirectory` failure was due to drifted `examples/Hello.java` content, not due to documentation edits.

---

## Remaining Issues

1. **No GitHub Actions workflows yet** — still a known 1.0 blocker; templates alone do not provide CI.
2. **`gh` CLI unavailable** in this environment — repository topics must be set manually (see recommendations below).
3. **Website (`website/`)** remains Chinese-first; only a stub `website/en/index.md` exists — full VitePress EN locale is a follow-up.
4. **Mass Chinese comments in source / example READMEs** — not bulk-translated this pass (prioritized public docs).
5. **Version skew**: CLI `0.32` vs `pom.xml` `0.21` — documented; not changed in this task.
6. **Full `mvn test`** not completed end-to-end here — run before release tagging.

---

## Recommended GitHub topics

Accurate for the current project (do **not** add “jvm-replacement”):

```text
programming-language
compiler
compiler-design
llvm
java
native-compiler
native-code
garbage-collection
systems-programming
```

Optional if desired: `ahead-of-time`, `programming-languages`.

Set via GitHub UI or:

```bash
gh repo edit itcrown2010/jnative \
  --add-topic programming-language \
  --add-topic compiler \
  --add-topic compiler-design \
  --add-topic llvm \
  --add-topic java \
  --add-topic native-compiler \
  --add-topic native-code \
  --add-topic garbage-collection \
  --add-topic systems-programming
```

---

## Recommended Next Steps

1. Enable GitHub Security Advisories + wire `SECURITY.md` contact.
2. Add CI workflow that builds the JAR and runs a smoke e2e on macOS/Linux.
3. Expand `website/en/` VitePress locale to mirror `docs/en/`.
4. Gradually English-prioritize high-traffic example READMEs without deleting Chinese.
5. Align `pom.xml` version with CLI `--version` in a dedicated PR.
6. Run full `mvn test` on a clean agent before calling 1.0 ready.
