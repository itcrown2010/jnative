# VS Code: no LSP / build

## Symptom
Highlighting works; build/LSP disabled.

## Cause
Untrusted workspace.

## Fix
Trust the folder. Set `jnative.jarPath` / compiler path per extension README.
