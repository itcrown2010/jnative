# Project templates

Runnable skeletons (not `jnative new --template` variants except mobile).

| Template | Feasible today | Notes |
|----------|----------------|-------|
| [console](console/) | Yes | Stable hello |
| [cli](cli/) | Yes | args stub |
| [server](server/) | Yes | Experimental HTTP |
| desktop | See `examples/desktop-sample/` | Experimental GUI — not duplicated (UI stack compile-sensitive) |
| studio-core | `examples/jnative-studio/` | Flagship CLI environment |
| mobile | `jnative new name --template mobile` | WebView+wasm — see mobile status |

Also: `jnative new <name>` creates a console project with tests.
