# Stale binary after failed build

## Symptom
App runs old behavior after a failed compile.

## Cause
Runner started previous artifact.

## Fix
Rebuild successfully; VS Code extension prefers failing closed—use latest `build-result` status. Delete output and rebuild.
