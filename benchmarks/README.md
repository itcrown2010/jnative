# Benchmarks

**Status:** Methodology stub (Phase 5). Measure only capabilities stable enough to time.

## Principles

- No vague “extremely fast” claims  
- No unfair Java/C++/Rust comparisons until methodology is fixed  
- Record host OS, arch, JNative `--version`, clang version  

## Candidate workloads

| Bench | Command sketch | Ready? |
|-------|----------------|--------|
| Compiler startup | `time java -jar jnative.jar --version` | Yes |
| Hello compile | time single-file `-o` | Yes |
| Executable startup | time `./Hello` | Yes |
| File IO round-trip | notes-cli / FileHello | Yes |
| Studio Core build project | time `StudioMain build` | Yes |
| HTTP one-shot | HttpHello + curl | Experimental |
| Thread counter | multithreading example | Experimental |

## Layout (future)

```text
benchmarks/
  scripts/run_basic.sh
  results/   # gitignored machine results
```

Add scripts only when they print machine-readable timings.
