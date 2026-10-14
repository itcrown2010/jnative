# Read a file

## Goal
Read an entire text file.

## Prerequisites
Native target; JNative JAR built.

## Code
```java
String text = File.readString("path.txt");
System.out.println(text);
```

## Explanation
`File.readString` is a builtin FFI intrinsic.

## Expected result
File contents printed.

## Common errors
Missing file → runtime failure. Use `File.exists` first.

## Related APIs
`File.readBytes` · example `examples/file-io/`
