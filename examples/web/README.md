# JNative Web Examples (0.22)

Small HTTP demos built on `stdlib/web/WebLib.java` (routing, middleware, static files, JSON helpers). Compile from the repo root after syncing stdlib sources into `target/classes`.

## Build

```bash
mkdir -p target/classes/stdlib/web target/classes/stdlib/http target/classes/stdlib/json
cp src/main/resources/stdlib/web/WebLib.java target/classes/stdlib/web/
cp src/main/resources/stdlib/http/HttpLib.java target/classes/stdlib/http/
cp src/main/resources/stdlib/json/JsonLib.java target/classes/stdlib/json/

java -cp target/classes io.jnative.Main examples/web/hello/App.java -o build/web/hello
java -cp target/classes io.jnative.Main examples/web/rest/App.java -o build/web/rest
java -cp target/classes io.jnative.Main examples/web/staticsite/App.java -o build/web/staticsite
java -cp target/classes io.jnative.Main examples/web/todo/App.java -o build/web/todo
```

Run each binary from the **repository root** so relative static paths resolve.

## Examples

| Directory | Port | Description |
|-----------|------|-------------|
| `hello/` | 18080 | Hello text, JSON greet, echo POST |
| `rest/` | 18180 | In-memory JSON CRUD (`/api/items`) |
| `staticsite/` | 18181 | Static files + `/api/info` |
| `todo/` | 18182 | Todo REST API + browser UI |
| `min/` | — | Single-request WebApp smoke test |
| `postcheck/` | — | HttpLib header/body parse check |

## Quick curl checks

```bash
./build/web/hello &
curl -s http://127.0.0.1:18080/
curl -s http://127.0.0.1:18080/hello/world

./build/web/rest &
curl -s http://127.0.0.1:18180/api/items
curl -s -X POST http://127.0.0.1:18180/api/items -H 'Content-Type: application/json' -d '{"name":"gamma"}'

./build/web/staticsite &
curl -s http://127.0.0.1:18181/
curl --path-as-is -s -o /dev/null -w '%{http_code}\n' 'http://127.0.0.1:18181/about/../../../etc/passwd'

./build/web/todo &
curl -s http://127.0.0.1:18182/api/todos
```

See `docs/jnative-0.22-build-report.md` for architecture acceptance and known limitations.
