package io.jnative.backend.llvm.runtime;

/**
 * RTTI helpers. ClassMetadata layout:
 * { ptr vtable, ptr super, i64 size, i32 flags, ptr name }
 */
public final class TypeRuntimeEmitter {
    private final StringBuilder sb;

    public TypeRuntimeEmitter(StringBuilder sb) {
        this.sb = sb;
    }

    public void emit() {
        sb.append("""
                define i1 @jnative_instanceof(ptr %obj, ptr %target) {
                entry:
                  %isnull = icmp eq ptr %obj, null
                  br i1 %isnull, label %false, label %walk
                false:
                  ret i1 false
                walk:
                  %meta0 = load ptr, ptr %obj, align 8
                  br label %loop
                loop:
                  %cur = phi ptr [ %meta0, %walk ], [ %next, %cont ]
                  %isnull2 = icmp eq ptr %cur, null
                  br i1 %isnull2, label %false2, label %check
                false2:
                  ret i1 false
                check:
                  %same = icmp eq ptr %cur, %target
                  br i1 %same, label %true, label %cont
                true:
                  ret i1 true
                cont:
                  %superptr = getelementptr { ptr, ptr, i64, i32, ptr, i32, ptr }, ptr %cur, i32 0, i32 1
                  %next = load ptr, ptr %superptr, align 8
                  br label %loop
                }

                define ptr @jnative_checked_cast(ptr %obj, ptr %target) {
                entry:
                  %isnull = icmp eq ptr %obj, null
                  br i1 %isnull, label %oknull, label %check
                oknull:
                  ret ptr null
                check:
                  %ok = call i1 @jnative_instanceof(ptr %obj, ptr %target)
                  br i1 %ok, label %pass, label %fail
                pass:
                  ret ptr %obj
                fail:
                  call void @jnative_throw_class_cast()
                  ret ptr null
                }

                """);
    }
}
