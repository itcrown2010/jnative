# Contributing to JNative

Thank you for considering a contribution.  
**English is preferred** for Issues, Pull Requests, and new public documentation. Chinese notes are welcome as supplements — see [CONTRIBUTING.zh-CN.md](CONTRIBUTING.zh-CN.md).

---

## License expectations

- Root license: **MIT** ([LICENSE](LICENSE)).
- Original project code copyright is held by an **individual** author (not a company) — [COPYRIGHT.md](COPYRIGHT.md), [NOTICE](NOTICE).
- **Opening a PR does not assign copyright** to the maintainer. Contributors retain copyright and license their contribution under MIT unless a separate CLA is introduced (none today).
- Do not submit code you lack rights to open-source, or dependencies incompatible with MIT.
- Do not strip third-party copyright/license headers.
- Do not add “non-commercial only” terms that conflict with MIT.

When adding third-party material, update [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and [docs/legal/THIRD_PARTY_SOURCES.md](docs/legal/THIRD_PARTY_SOURCES.md).

---

## Development setup

See [docs/en/getting-started.md](docs/en/getting-started.md).

```bash
mvn -q -DskipTests package
java -jar target/jnative.jar doctor
mvn test   # full suite; use -Dtest=… while iterating
```

Requirements: Java 21+, Maven 3.9+, `clang` on `PATH`.

---

## How to add a language / compiler feature

1. Implement in the appropriate package under `src/main/java/io/jnative/` (frontend, semantic, IR, opt, LLVM backend, runtime emitters as needed).
2. Add or extend tests under `src/test/java/` (prefer e2e when behavior is user-visible).
3. Update **English** docs under `docs/en/` first if the public surface changes; keep Chinese tutorials in sync when they would otherwise contradict.
4. Do **not** document unsupported Java SE features as working.

---

## How to add compiler tests

- Unit tests for isolated passes/utilities.
- End-to-end tests that compile and run a small program when possible.
- Note host OS / toolchain in the PR if the test is platform-specific.

---

## Reporting bugs

Use the GitHub bug report template (`.github/ISSUE_TEMPLATE/bug_report.md`). Include:

- JNative version (`--version`)
- OS / architecture
- LLVM / clang version
- Java version
- Minimal source
- Expected vs actual behavior
- Reproduction steps and logs

---

## Pull requests

Use `.github/pull_request_template.md`. Keep PRs focused; avoid unrelated refactors. Ensure:

- [ ] Relevant tests pass
- [ ] Documentation updated (English-first)
- [ ] No secrets committed

---

## Coding & documentation conventions

- Prefer clear, small commits with English messages.
- Public docs: accurate, concise, no unsupported claims.
- Source comments for complex compiler/runtime code: prefer English.
- Status language: **Implemented** / **Experimental** / **Planned** / **Unsupported** — match [docs/developer/FACT_INVENTORY.md](docs/developer/FACT_INVENTORY.md) evidence.

---

## Sensitive data

No private keys, tokens, production passwords, or real personal data in the tree. Use documented demo credentials only.
