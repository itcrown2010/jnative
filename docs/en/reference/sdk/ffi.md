# FFI API

**Status:** Experimental · **Source:** FFI annotations + `FfiEndToEndTest` · Example `examples/NativeCallback.java`

## Overview

Call native C functions and receive callbacks where supported.

## Examples

`examples/NativeCallback.java` · recipe [call-native-function](../../recipes/call-native-function.md)

## Platform Notes

Native desktop/CLI targets only. **Not** available on wasm32-browser. FFI ≠ mobile app SDK.

## Known Limitations

Platform ABI differences; keep examples minimal and tested.
