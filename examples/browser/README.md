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

## Float / double (`float-calc`)

```bash
./examples/browser/float-calc/scripts/verify.sh
python3 -m http.server 8766 --directory examples/browser/float-calc/dist
```

Arithmetic is executed in WASM (`f32`/`f64`). Contract: `docs/wasm32-browser-float-contract.md`.
