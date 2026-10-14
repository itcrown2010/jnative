# HTTP example appears hung

## Symptom
`HttpHello` waits forever.

## Cause
Server blocks on `accept` until a request arrives.

## Fix
`curl` the listening port; kill the process when done. One-shot examples exit after one request.
