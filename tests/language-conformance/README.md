# Language conformance suite (shared)

Scaffold for common language tests that both the reference compiler and future self-hosted frontend must pass.

Organize by feature when tests are added:

```text
classes/ inheritance/ generics/ lambdas/ exceptions/ …
```

Today: lexer corpus lives under `tests/selfhost/lexer/` until parser work begins.
