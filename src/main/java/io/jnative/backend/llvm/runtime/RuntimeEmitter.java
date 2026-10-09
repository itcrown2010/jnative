package io.jnative.backend.llvm.runtime;

/**
 * Orchestrates emission of JNative runtime helpers into LLVM IR text.
 * @author 张晓达
 */
public final class RuntimeEmitter {
    private final StringBuilder sb;

    public RuntimeEmitter(StringBuilder sb) {
        this.sb = sb;
    }

    public void emitAll() {
        emitDeclarations();
        new MemoryRuntimeEmitter(sb).emit();
        new ExceptionRuntimeEmitter(sb).emit();
        new GcRuntimeEmitter(sb).emit();
        new ArrayRuntimeEmitter(sb).emit();
        new StringRuntimeEmitter(sb).emit();
        new FloatRuntimeEmitter(sb).emit();
        new BytesRuntimeEmitter(sb).emit();
        new TypeRuntimeEmitter(sb).emit();
        new IoRuntimeEmitter(sb).emit();
        new FfiRuntimeEmitter(sb).emit();
        new FileRuntimeEmitter(sb).emit();
        new NetworkRuntimeEmitter(sb).emit();
        new ThreadRuntimeEmitter(sb).emit();
        new CallableRuntimeEmitter(sb).emit();
        new AsyncRuntimeEmitter(sb).emit();
    }

    private void emitDeclarations() {
        sb.append("declare ptr @malloc(i64 noundef)\n");
        sb.append("declare void @free(ptr noundef)\n");
        sb.append("declare ptr @realloc(ptr noundef, i64 noundef)\n");
        sb.append("declare ptr @memset(ptr noundef, i32 noundef, i64 noundef)\n");
        sb.append("declare ptr @memcpy(ptr noundef, ptr noundef, i64 noundef)\n");
        sb.append("declare i32 @printf(ptr noundef, ...)\n");
        sb.append("declare i32 @snprintf(ptr noundef, i64 noundef, ptr noundef, ...)\n");
        sb.append("declare double @strtod(ptr noundef, ptr noundef)\n");
        sb.append("declare i32 @puts(ptr noundef)\n");
        sb.append("declare void @exit(i32 noundef)\n");
        sb.append("declare void @abort()\n");
        sb.append("declare i64 @strlen(ptr noundef)\n");
        sb.append("declare ptr @getenv(ptr noundef)\n");
        sb.append("declare i64 @atol(ptr noundef)\n");
        sb.append("declare i32 @atexit(ptr noundef)\n");
        sb.append("declare void @jn_nogc_enter()\n");
        sb.append("declare void @jn_nogc_leave()\n");
        sb.append("declare i32 @jn_nogc_depth()\n");
        // libc file I/O (platform layer for 0.8.1; File API does not expose FILE*)
        sb.append("declare ptr @fopen(ptr noundef, ptr noundef)\n");
        sb.append("declare i32 @fclose(ptr noundef)\n");
        sb.append("declare i64 @fread(ptr noundef, i64 noundef, i64 noundef, ptr noundef)\n");
        sb.append("declare i64 @fwrite(ptr noundef, i64 noundef, i64 noundef, ptr noundef)\n");
        sb.append("declare i32 @fseek(ptr noundef, i64 noundef, i32 noundef)\n");
        sb.append("declare i64 @ftell(ptr noundef)\n");
        sb.append("declare i32 @remove(ptr noundef)\n");
        sb.append('\n');
        emitCString(".fmt.i32", "%d\n");
        emitCString(".fmt.i64", "%lld\n");
        emitCString(".fmt.double", "%g\n");
        emitCString(".fmt.char", "%c\n");
        emitCString(".fmt.bool.true", "true");
        emitCString(".fmt.bool.false", "false");
        emitCString(".jn.nullmsg", "JNative runtime error: null reference");
        emitCString(".jn.boundsmsg", "JNative runtime error: array index out of bounds");
        emitCString(".jn.negarr", "JNative runtime error: negative array size");
        emitCString(".jn.castmsg", "JNative runtime error: invalid cast");
        emitCString(".jn.uncaught.prefix", "Exception in thread \"main\": ");
        sb.append('\n');
    }

    private void emitCString(String name, String content) {
        byte[] bytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int len = bytes.length + 1;
        sb.append('@').append(name).append(" = private unnamed_addr constant [")
                .append(len).append(" x i8] c\"");
        for (byte b : bytes) {
            int v = b & 0xff;
            if (v >= 32 && v < 127 && v != '"' && v != '\\') {
                sb.append((char) v);
            } else {
                sb.append(String.format("\\%02X", v));
            }
        }
        sb.append("\\00\", align 1\n");
    }
}
