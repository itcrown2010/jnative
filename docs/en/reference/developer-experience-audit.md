# Developer Experience Audit (Phase 4)

**Date:** 2026-10-13  
**Method:** Count pages/examples present in-repo after Phase 4 work; statuses from capability audit (not invented).

## Coverage counts

| Area | Documented units | Notes |
|------|----------------:|-------|
| Language tour pages (`docs/en/language/*.md`) | **24** | includes index, basics, for-java, memory, collections, … |
| SDK guide pages (`docs/en/sdk/*.md`) | **18** | includes index, getting-started, filesystem, concurrency, … |
| Cookbook recipes (`docs/en/cookbook/*.md`) | **15** | only feasible APIs |
| Tutorials (`docs/en/tutorials/*.md`) | **11** | includes rewritten 30-min + building-an-application |
| VS Code pages | **10** | index/features/build-and-run added |
| Web pages | **13** | + index; WebSocket explicitly not claimed |
| Desktop pages | **8** | + index |
| Mobile pages | **9** | + status + index |
| Troubleshooting pages | **7** | real issues from audit |
| Runnable templates | **3** | console, cli, server |
| New tutorial example | **1** | `examples/tutorials/cli-todo` verified |

## Qualitative coverage

| Area | Assessment |
|------|------------|
| Language coverage | High for implemented surface; not a full language spec book |
| SDK coverage | Module guides + reference sketches; per-method completeness still incomplete vs full stdlib class list |
| Tutorial coverage | Strong beginner path (Todo → Notes → HTTP) |
| Example coverage | Core capabilities have examples; numbered `01-` tree not required (existing names referenced) |
| VS Code coverage | Matches 0.8.x extension README |
| Web coverage | Honest matrix; no fake framework |
| Desktop coverage | Honest; macOS GUI experimental |
| Mobile coverage | Status page leads; no false native UI claim |
| Automated doc validation | `tools/check-docs` + optional `verify-en-examples.sh`; CI workflow `docs.yml` |
| SDK index | `docs/en/_generated/sdk-index.json` via `scripts/docs/generate-sdk-index.py` |

## Remaining gaps

1. JsonNumber stringify bug (numbers → 0)  
2. Directory create/list recipes still Partial  
3. Full per-method SDK reference for every stdlib class  
4. Linux/Windows verified hosts  
5. Example compile in CI default job (opt-in only)  
6. Desktop GUI minimal template not added (points to existing sample)  

## Journey completeness

```text
Discover (README / start-here)
 → Install
 → 30-min Todo
 → Language tour / SDK / Cookbook
 → VS Code
 → Build application tutorial
 → Platform pages (web/desktop/mobile status)
 → Architecture / contribute
```

This path is navigable from [start-here.md](../start-here.md).
