# openssl-tls

Platform TLS backend metadata package for JNative **0.31** SecureTransport.

This package does **not** reimplement TLS or cryptography. It declares that the host
OpenSSL (`libssl` / `libcrypto`) is the native link target for the `TlsLib` stdlib and
`jnative_tls` runtime shim. Use it from `jnative.toml` when building native servers or
clients that call `TlsContext` / `TlsConnection`.

- **0.30** package manager: registry metadata, capability checks (`TLS_NATIVE`, `NATIVE_FFI`, `RAW_SOCKET`)
- **0.31** TLS abstraction: stdlib `tls/TlsLib.java` + POSIX `jnative_tls.c`

Generate development certificates with:

```bash
jnative cert dev --out ./certs
jnative serve App.java --port 8443 --tls ./certs/server.crt ./certs/server.key
```
