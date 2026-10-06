# JNative Browser Examples (0.26)

Build:

```bash
java -cp target/classes io.jnative.Main build \
  --target wasm32-browser \
  examples/browser/hello-dom/App.java \
  -o examples/browser/hello-dom/dist
```

Serve:

```bash
python3 -m http.server 3000 --directory examples/browser/hello-dom/dist
```

Open `http://localhost:3000` — title becomes **Hello from JNative**.
