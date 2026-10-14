# Recipe: Copy a file

```java
byte[] data = File.readBytes("src.bin");
File.writeBytes("dst.bin", data);
```

No dedicated `File.copy` builtin—compose read/write.
