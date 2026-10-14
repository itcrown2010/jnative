# JSON numbers become 0

## Symptom
`putNumber` / `JsonNumber` stringify shows `0`.

## Cause
Implementation gap observed on CLI 0.32 (Phase 3 audit).

## Fix
Use string fields or verify after a fix lands; see capability audit.
