#!/usr/bin/env bash
# Author: 张晓达
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
JAR="$ROOT/target/jnative.jar"
OUT="$ROOT/.build-staging/docs-verify"
mkdir -p "$OUT"
[[ -f "$JAR" ]] || { echo "missing $JAR"; exit 1; }

pass=()
check() {
  local name="$1"; shift
  echo "==> $name"
  "$@"
  pass+=("$name")
}

check hello-world bash -c "java -jar \"$JAR\" \"$ROOT/examples/hello-world/src/Main.jn\" -o \"$OUT/Hello\" -O0 && out=\$(\"$OUT/Hello\"); test \"\$out\" = 'Hello, JNative!'"
check hello-java bash -c "java -jar \"$JAR\" \"$ROOT/examples/Hello.java\" -o \"$OUT/HelloJava\" -O0 && out=\$(\"$OUT/HelloJava\"); test \"\$out\" = 'Hello JNative'"
check file-io bash -c "cd \"$OUT\" && java -jar \"$JAR\" \"$ROOT/examples/file-io/FileHello.java\" -o FileHello -O0 && out=\$(./FileHello); test \"\$out\" = 'Hello file'"
check lambda bash -c "java -jar \"$JAR\" \"$ROOT/examples/LambdaClosure.java\" -o \"$OUT/Lam\" -O0"
check http-compile bash -c "java -jar \"$JAR\" \"$ROOT/examples/http-server/HttpHelloServer.java\" -o \"$OUT/HttpHello\" -O0"
check web-hello-compile bash -c "java -jar \"$JAR\" \"$ROOT/examples/web/hello/App.java\" -o \"$OUT/WebHello\" -O0"
check json-demo bash -c "java -jar \"$JAR\" \"$ROOT/examples/json-demo/JsonDemo.java\" -o \"$OUT/JsonDemo\" -O0 && out=\$(\"$OUT/JsonDemo\" | tr '\n' '|'); echo \"\$out\" | grep -q 'true'"
check notes-cli bash -c "java -jar \"$JAR\" \"$ROOT/examples/notes-cli/src/Main.jn\" -o \"$OUT/notes\" -O0 && cd \"$OUT\" && ./notes create t 'x' && out=\$(./notes read t); test \"\$out\" = 'x'"
check multithreading bash -c "java -jar \"$JAR\" \"$ROOT/examples/multithreading/Main.java\" -o \"$OUT/ThreadDemo\" -O0 && out=\$(\"$OUT/ThreadDemo\" | tail -1); test \"\$out\" = '2000'"
check tcp-echo-compile bash -c "java -jar \"$JAR\" \"$ROOT/examples/tcp-echo/EchoMain.java\" -o \"$OUT/TcpEcho\" -O0"
check cli-todo bash -c "java -jar \"$JAR\" \"$ROOT/examples/tutorials/cli-todo/TodoApp.java\" -o \"$OUT/Todo\" -O0 && cd \"$OUT\" && ./Todo add a && ./Todo add b && out=\$(./Todo list); echo \"\$out\" | grep -q milk || echo \"\$out\" | grep -q a"
check tpl-console bash -c "java -jar \"$JAR\" \"$ROOT/examples/templates/console/src/Main.jn\" -o \"$OUT/TplConsole\" -O0 && test \"\$(\"$OUT/TplConsole\")\" = 'Hello, JNative!'"

echo "==> http-curl"
"$OUT/HttpHello" >"$OUT/http.log" 2>&1 &
pid=$!
ok=0
for i in 1 2 3 4 5 6 7 8 9 10; do
  body=$(curl -s --max-time 1 http://127.0.0.1:18081/ 2>/dev/null || true)
  if [[ "$body" == "Hello JNative" ]]; then ok=1; break; fi
  sleep 0.2
done
kill "$pid" 2>/dev/null || true
wait "$pid" 2>/dev/null || true
if [[ "$ok" != 1 ]]; then
  echo "http-curl failed; log:"; cat "$OUT/http.log" || true
  exit 1
fi
pass+=(http-curl)

echo "GUIDE_EN_EXAMPLES_OK ${pass[*]}"
