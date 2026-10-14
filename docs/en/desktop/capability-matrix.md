# Desktop Capability Matrix

| Capability | Current Status | Evidence |
|---|---|---|
| Native executable generation | Stable (macOS arm64 verified) | `jnative build` / single-file `-o` |
| macOS GUI (UiLib) | Experimental | `examples/desktop-sample`, `UiEndToEndTest`, activity-monitor |
| Linux GUI | Unverified | no host evidence in this audit |
| Windows GUI | Unverified / Partial | no host evidence |
| Packaging `.app` | Experimental | sample app bundles under `build/` in some examples |
| System APIs via FFI | Experimental | native FFI on desktop host |
| Cross-compile from mac→Win/Linux | Not Supported / Unverified | document only host-native builds as verified |
| “Write once UI everywhere” | Not Supported | product explicitly rejects this claim |

### Summary

Desktop **CLI/tools** on macOS arm64 are the strongest path. Desktop **GUI** exists and has samples on macOS but remains **experimental**. Treat Win/Linux GUI as unverified until CI hosts exist.

See [overview](overview.md).
