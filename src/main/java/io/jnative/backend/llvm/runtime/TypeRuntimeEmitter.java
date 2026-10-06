package io.jnative.backend.llvm.runtime;

/**
 * RTTI helpers. ClassMetadata layout:
 * { ptr vtable, ptr super, i64 size, i32 flags, ptr name, i32 refCount, ptr refOff,
 *   i32 ifaceCount, ptr ifaceEntries }
 * ifaceEntries: [{ i32 id, ptr itable }...]
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
                  %superptr = getelementptr %jn.ClassMeta, ptr %cur, i32 0, i32 1
                  %next = load ptr, ptr %superptr, align 8
                  br label %loop
                }

                define i1 @jnative_instanceof_interface(ptr %obj, i32 %ifaceId) {
                entry:
                  %isnull = icmp eq ptr %obj, null
                  br i1 %isnull, label %false, label %lookup
                false:
                  ret i1 false
                lookup:
                  %itable = call ptr @jnative_itable_lookup(ptr %obj, i32 %ifaceId)
                  %ok = icmp ne ptr %itable, null
                  ret i1 %ok
                }

                define ptr @jnative_itable_lookup(ptr %obj, i32 %ifaceId) {
                entry:
                  %isnull = icmp eq ptr %obj, null
                  br i1 %isnull, label %miss, label %loadmeta
                miss:
                  ret ptr null
                loadmeta:
                  %meta = load ptr, ptr %obj, align 8
                  %countPtr = getelementptr %jn.ClassMeta, ptr %meta, i32 0, i32 7
                  %count = load i32, ptr %countPtr, align 4
                  %entriesPtr = getelementptr %jn.ClassMeta, ptr %meta, i32 0, i32 8
                  %entries = load ptr, ptr %entriesPtr, align 8
                  %entriesNull = icmp eq ptr %entries, null
                  br i1 %entriesNull, label %miss, label %loop
                loop:
                  %i = phi i32 [ 0, %loadmeta ], [ %inext, %cont ]
                  %done = icmp sge i32 %i, %count
                  br i1 %done, label %miss, label %check
                check:
                  %ep = getelementptr %jn.IfaceEntry, ptr %entries, i32 %i
                  %idPtr = getelementptr %jn.IfaceEntry, ptr %ep, i32 0, i32 0
                  %id = load i32, ptr %idPtr, align 4
                  %match = icmp eq i32 %id, %ifaceId
                  br i1 %match, label %hit, label %cont
                hit:
                  %itPtr = getelementptr %jn.IfaceEntry, ptr %ep, i32 0, i32 1
                  %itable = load ptr, ptr %itPtr, align 8
                  ret ptr %itable
                cont:
                  %inext = add i32 %i, 1
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

                define ptr @jnative_checked_cast_interface(ptr %obj, i32 %ifaceId) {
                entry:
                  %isnull = icmp eq ptr %obj, null
                  br i1 %isnull, label %oknull, label %check
                oknull:
                  ret ptr null
                check:
                  %ok = call i1 @jnative_instanceof_interface(ptr %obj, i32 %ifaceId)
                  br i1 %ok, label %pass, label %fail
                pass:
                  ret ptr %obj
                fail:
                  call void @jnative_throw_class_cast()
                  ret ptr null
                }

                define void @jnative_abstract_method_error() {
                entry:
                  call i32 @puts(ptr @.jn.abstract.msg)
                  call void @exit(i32 1)
                  unreachable
                }

                @.jn.abstract.msg = private unnamed_addr constant [32 x i8] c"JNative: abstract method called\\00", align 1

                """);
    }
}
