# Security Policy

## Supported versions

JNative is under active development (CLI currently reports **0.32** / `1.0-dev`). There is no long-term support promise yet. Security fixes are applied on a best-effort basis to the default branch.

## Reporting a vulnerability

Please **do not** open a public GitHub Issue for security-sensitive reports if disclosure could harm users.

Preferred path:

1. Contact the repository maintainer privately (GitHub Security Advisories if enabled on the repo, or the email listed on the maintainer’s GitHub profile).
2. Include: affected version (`jnative --version`), OS/arch, impact, and a minimal reproduction if possible.
3. Allow reasonable time for assessment before public disclosure.

## Scope notes

- JNative is an experimental compiler/runtime. Do not use it for high-assurance production security boundaries without independent review.
- Known crypto/auth defaults and platform gaps are tracked in project docs (e.g. `docs/1.0-release-blockers.md`). Treat flagged insecure defaults as **do not use in production**.
- Never commit secrets into the repository.

## Non-security bugs

Use the public bug report template under `.github/ISSUE_TEMPLATE/`.
