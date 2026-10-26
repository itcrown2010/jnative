# CLI Todo (30-minute tutorial)

Persistent todo list using `ArrayList` + `File`.

```bash
java -jar ../../../target/jnative.jar TodoApp.java -o ../../../build/todo
cd /tmp
/path/to/jnative/build/todo add milk
/path/to/jnative/build/todo add eggs
/path/to/jnative/build/todo list
/path/to/jnative/build/todo done milk
/path/to/jnative/build/todo list
```

Expected after first add: `added`. After list with two items: two lines. After `done milk`: `done`, then list shows `eggs`.

Docs: `docs/en/tutorials/30-minutes.md`
