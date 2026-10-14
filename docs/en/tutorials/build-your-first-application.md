# Build Your First Application: JNative Notes

After Hello World, build a tiny **notes CLI** with file persistence.

## Goal

- create a note  
- read a note  
- list notes  
- delete a note  

## Project

Use the verified sample:

```text
examples/notes-cli/
```

Or recreate it:

```bash
mkdir -p notes-app && cd notes-app
# copy examples/notes-cli/src/Main.jn
```

## Build

```bash
java -jar /path/to/target/jnative.jar examples/notes-cli/src/Main.jn -o build/notes
```

## Try it

```bash
cd /tmp
/path/to/build/notes create shopping "milk eggs"
/path/to/build/notes list
/path/to/build/notes read shopping
/path/to/build/notes delete shopping
```

## How it works

| Feature | API |
|---------|-----|
| Persist text | `File.writeString` / `File.readString` |
| Existence | `File.exists` |
| Delete | `File.delete` |
| CLI | `String[] args` |

## Optional HTTP interface

The SDK can host `HttpServer`, but this tutorial stays file-only. When you are ready, wrap the same helpers behind `HttpServer.listen` using [http-server tutorial](http-server.md)—keep validation and paths explicit.

## Next steps

- [file-io example](../../../examples/file-io/README.md)  
- [JSON demo](../../../examples/json-demo/README.md)  
- [HTTP server](http-server.md)  
