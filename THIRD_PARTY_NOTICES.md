# Third-Party Notices

This file lists third-party software that may be included with JNative distributions
or required when linking generated programs. It is a **pre-release draft** for review.

The JNative project license is stated in the root `LICENSE` file (MIT). That file does
**not** re-license third-party components listed below.

For a fuller inventory (including development-only items), see
`docs/legal/THIRD_PARTY_SOURCES.md`.

---

## Bundled with the VS Code extension (VSIX)

### vscode-languageclient (and related LSP libraries)

- Copyright (c) Microsoft Corporation
- License: MIT
- Full text: see the package under `node_modules` when building the extension, or
  `third_party_licenses/MIT-vscode-languageclient.txt`

---

## Used when linking certain native features (not vendored as source in this repo)

### OpenSSL

- Used by the optional TLS native runtime (`jnative_tls.c`) via system/Homebrew libraries.
- License: OpenSSL License and/or Apache License 2.0 depending on OpenSSL version.
- Distributors of binaries that **link** OpenSSL must comply with the OpenSSL license
  terms applicable to that version (typically attribution / notice).
- Reference: https://www.openssl.org/source/license.html

### zlib

- Used by the optional graphics native runtime (`jnative_graphics.c`) via system zlib.
- License: zlib License
- Text: `third_party_licenses/zlib.txt`

---

## Example / Android scaffolding

### Gradle Wrapper

- Copyright © 2015-2021 the original authors
- License: Apache License 2.0
- Location: `examples/**/gradlew` and wrapper JARs
- Text: `third_party_licenses/Apache-2.0.txt`

---

## Test-only (not shipped in the compiler runtime)

### JUnit 5

- License: Eclipse Public License 2.0
- Scope: Maven `test` dependency only
- Text: `third_party_licenses/EPL-2.0.txt`

---

## Unverified / quarantine

Items that may incorporate third-party tables or unknown provenance are listed in
`docs/legal/quarantine/README.md` and must not be treated as cleared for publication
until reviewed.
