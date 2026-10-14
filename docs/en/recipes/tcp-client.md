# Recipe: TCP client

Use `TcpSocket` connect APIs from builtins / net stdlib (see `SemanticAnalyzer.installNetworkBuiltins` and `NetworkEndToEndTest` for the exact connect helper your version exposes). Prefer HTTP client for HTTP workloads.
