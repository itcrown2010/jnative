# admin — JNative 0.29 incremental / `jnative dev` fixture

作者：张晓达

```text
shared  ←  server
   ↑    ←  browser
   ↑    ←  desktop
```

```bash
# from repo root after mvn package
java -cp target/classes io.jnative.Main build --project examples/dev/admin --explain
java -cp target/classes io.jnative.Main dev --project examples/dev/admin
```

Then edit `browser/UsersPage.java` (browser-only), `browser/public/app.css` (CSS hot reload),
or `server/UserRepository.java` / `shared/User.java`.
