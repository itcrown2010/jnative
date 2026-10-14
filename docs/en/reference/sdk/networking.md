# Networking API (TCP)

**Status:** Experimental · **Source:** builtins (`TcpSocket`, `TcpServer`) + `stdlib/net/`

## Overview

Low-level TCP. HTTP builds on these sockets.

## Types / Methods (builtins — see SemanticAnalyzer)

Typical surface used in examples:

| API | Usage |
|-----|--------|
| `TcpServer.listen(host, port)` | Bind |
| `server.accept()` | Blocking accept → `TcpSocket` |
| `socket.readBytes(n)` | Read up to n bytes |
| `socket.write(bytes)` | Write |
| `socket.close()` | Close |

Exact overload list: `installNetworkBuiltins` in `SemanticAnalyzer.java`.

## Examples

`examples/EchoServer.java`, `examples/tcp-echo/` · recipes [tcp-client](../../recipes/tcp-client.md), [tcp-server](../../recipes/tcp-server.md)

## Platform Notes

Native only. No raw sockets on wasm32-browser.

## Known Limitations

IPv6 covered in `Ipv6TcpEndToEndTest` — still experimental.
