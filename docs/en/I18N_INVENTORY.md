# Documentation I18N Inventory

**Date:** 2026-10-13  
**Scope:** Pre-migration audit for English-first public docs.

## Primary surfaces (before migration)

| Area | Finding |
|------|---------|
| `README.md` | Mixed EN/ZH; English pipeline sketch present; Chinese licensing notes |
| `docs/` | ~245 Markdown files; many EN technical notes; Chinese tutorials (`docs/tutorial/`) |
| `docs/developer/` | Chinese-first fact inventory / stages |
| `website/` | VitePress Chinese-first; `website/en/index.md` stub only |
| `CONTRIBUTING.md` | Chinese draft (legal/licensing) |
| `CODE_OF_CONDUCT` | Missing |
| `SECURITY` | Missing (only `docs/security-review.md`) |
| `.github/` | Missing (no workflows, issue/PR templates) — known 1.0 blocker |
| Examples READMEs | Mostly Chinese or mixed |
| CLI `--help` | English |
| CLI `--version` | `JNative 0.32` (banner also says `1.0-dev`) |

## Language policy (adopted)

- English is authoritative for public landing docs (`README.md`, `docs/en/`).
- Chinese retained as supplementary (`README.zh-CN.md`, `docs/zh-CN/`, existing `docs/tutorial/`).
- Do not delete Chinese tutorials in this pass; link from zh-CN index.

## Verified Hello World (this migration)

```text
java -jar target/jnative.jar examples/guide-hello/src/Main.jn -o /tmp/Hello -O0
/tmp/Hello  →  Hello, JNative!
```
