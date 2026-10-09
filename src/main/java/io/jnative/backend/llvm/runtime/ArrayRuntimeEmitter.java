package io.jnative.backend.llvm.runtime;

/**
 * Array runtime. Layout: { ClassMetadata* @0, i32 length @8, padding to 16, elements... }
 * @author 张晓达
 */
public final class ArrayRuntimeEmitter {
    private final StringBuilder sb;

    public ArrayRuntimeEmitter(StringBuilder sb) {
        this.sb = sb;
    }

    public void emit() {
        // jnative_array_alloc(elemSize, length, meta) -> ptr
        sb.append("define ptr @jnative_array_alloc(i64 %elemSize, i32 %length, ptr %meta) {\n");
        sb.append("entry:\n");
        sb.append("  %neg = icmp slt i32 %length, 0\n");
        sb.append("  br i1 %neg, label %bad, label %oksize\n");
        sb.append("bad:\n");
        sb.append("  call void @jnative_throw_negative_array_size()\n");
        sb.append("  ret ptr null\n");
        sb.append("oksize:\n");
        sb.append("  %len64 = sext i32 %length to i64\n");
        sb.append("  %payload = mul i64 %len64, %elemSize\n");
        sb.append("  %total = add i64 %payload, 16\n");
        sb.append("  %p = call ptr @jnative_alloc_zeroed(i64 %total)\n");
        sb.append("  store ptr %meta, ptr %p, align 8\n");
        sb.append("  %lenptr = getelementptr i8, ptr %p, i64 8\n");
        sb.append("  store i32 %length, ptr %lenptr, align 4\n");
        sb.append("  ret ptr %p\n");
        sb.append("}\n\n");

        sb.append("define void @jnative_array_bounds_check(ptr %arr, i32 %index) {\n");
        sb.append("entry:\n");
        sb.append("  call void @jnative_null_check(ptr %arr)\n");
        sb.append("  %lenptr = getelementptr i8, ptr %arr, i64 8\n");
        sb.append("  %len = load i32, ptr %lenptr, align 4\n");
        sb.append("  %lt0 = icmp slt i32 %index, 0\n");
        sb.append("  %ge = icmp sge i32 %index, %len\n");
        sb.append("  %bad = or i1 %lt0, %ge\n");
        sb.append("  br i1 %bad, label %trap, label %ok\n");
        sb.append("trap:\n");
        sb.append("  call void @jnative_throw_aioobe()\n");
        sb.append("  ret void\n");
        sb.append("ok:\n");
        sb.append("  ret void\n");
        sb.append("}\n\n");

        sb.append("define i32 @jnative_array_length(ptr %arr) {\n");
        sb.append("entry:\n");
        sb.append("  call void @jnative_null_check(ptr %arr)\n");
        sb.append("  %lenptr = getelementptr i8, ptr %arr, i64 8\n");
        sb.append("  %len = load i32, ptr %lenptr, align 4\n");
        sb.append("  ret i32 %len\n");
        sb.append("}\n\n");

        sb.append("define ptr @jnative_array_elem_ptr(ptr %arr, i32 %index, i64 %elemSize) {\n");
        sb.append("entry:\n");
        sb.append("  call void @jnative_array_bounds_check(ptr %arr, i32 %index)\n");
        sb.append("  %p = call ptr @jnative_array_elem_ptr_unchecked(ptr %arr, i32 %index, i64 %elemSize)\n");
        sb.append("  ret ptr %p\n");
        sb.append("}\n\n");

        sb.append("define ptr @jnative_array_elem_ptr_unchecked(ptr %arr, i32 %index, i64 %elemSize) {\n");
        sb.append("entry:\n");
        sb.append("  %idx64 = sext i32 %index to i64\n");
        sb.append("  %off = mul i64 %idx64, %elemSize\n");
        sb.append("  %base = add i64 %off, 16\n");
        sb.append("  %p = getelementptr i8, ptr %arr, i64 %base\n");
        sb.append("  ret ptr %p\n");
        sb.append("}\n\n");
    }
}
