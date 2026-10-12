/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.backend.llvm.runtime;

/**
 * Network runtime orchestration (0.8.2). Public TcpSocket/TcpServer APIs lower here;
 * OS sockets stay in the PlatformSocket C layer ({@code jnative_plat_tcp_*}).
 * @author 张晓达
 */
public final class NetworkRuntimeEmitter {
    private final StringBuilder sb;

    public NetworkRuntimeEmitter(StringBuilder sb) {
        this.sb = sb;
    }

    public void emit() {
        new SocketRuntimeEmitter(sb).emit();
        new DnsRuntimeEmitter(sb).emit();
        emitHelpers();
        emitConnect();
        emitListen();
        emitAccept();
        emitRead();
        emitWrite();
        emitClose();
        emitTimeouts();
    }

    private void emitHelpers() {
        sb.append("""
                declare i32 @jnative_plat_tcp_connect(ptr, i32, i32, ptr, ptr, i64)
                declare i32 @jnative_plat_tcp_listen(ptr, i32, ptr, ptr, i64)
                declare i32 @jnative_plat_tcp_accept(i64, ptr, ptr, i64)
                declare i32 @jnative_plat_tcp_read(i64, ptr, i32, ptr, ptr, i64)
                declare i32 @jnative_plat_tcp_read_all(i64, ptr, ptr, ptr, i64)
                declare i32 @jnative_plat_tcp_write_all(i64, ptr, i32, ptr, i64)
                declare i32 @jnative_plat_tcp_close(i64)
                declare i32 @jnative_plat_tcp_set_timeouts(i64, i32, i32, ptr, i64)

                """);
        emitCString(".jn.net.any", "0.0.0.0");
        emitCString(".jn.net.closed", "Socket is closed");
        emitCString(".jn.net.badport", "port out of range (0..65535)");
        sb.append('\n');

        sb.append("""
                define void @jnative_throw_net(i32 %code, ptr %errbuf) {
                entry:
                  %len = call i64 @strlen(ptr %errbuf)
                  %len32 = trunc i64 %len to i32
                  %msg = call ptr @jnative_string_from_literal(ptr %errbuf, i32 %len32, ptr @meta.String)
                  %isHost = icmp eq i32 %code, 2
                  br i1 %isHost, label %host, label %chkTimeout
                host:
                  %oh = call ptr @jnative_alloc_zeroed(i64 24)
                  store ptr @meta.UnknownHostException, ptr %oh, align 8
                  %mh = getelementptr i8, ptr %oh, i64 8
                  store ptr %msg, ptr %mh, align 8
                  call void @jnative_exception_throw(ptr %oh)
                  ret void
                chkTimeout:
                  %isTo = icmp eq i32 %code, 3
                  br i1 %isTo, label %timeout, label %chkConn
                timeout:
                  %ot = call ptr @jnative_alloc_zeroed(i64 24)
                  store ptr @meta.SocketTimeoutException, ptr %ot, align 8
                  %mt = getelementptr i8, ptr %ot, i64 8
                  store ptr %msg, ptr %mt, align 8
                  call void @jnative_exception_throw(ptr %ot)
                  ret void
                chkConn:
                  %isConn = icmp eq i32 %code, 1
                  br i1 %isConn, label %conn, label %chkArg
                conn:
                  %oc = call ptr @jnative_alloc_zeroed(i64 24)
                  store ptr @meta.ConnectException, ptr %oc, align 8
                  %mc = getelementptr i8, ptr %oc, i64 8
                  store ptr %msg, ptr %mc, align 8
                  call void @jnative_exception_throw(ptr %oc)
                  ret void
                chkArg:
                  %isArg = icmp eq i32 %code, 5
                  br i1 %isArg, label %arg, label %sock
                arg:
                  %oa = call ptr @jnative_alloc_zeroed(i64 24)
                  store ptr @meta.IllegalArgumentException, ptr %oa, align 8
                  %ma = getelementptr i8, ptr %oa, i64 8
                  store ptr %msg, ptr %ma, align 8
                  call void @jnative_exception_throw(ptr %oa)
                  ret void
                sock:
                  %os = call ptr @jnative_alloc_zeroed(i64 24)
                  store ptr @meta.SocketException, ptr %os, align 8
                  %ms = getelementptr i8, ptr %os, i64 8
                  store ptr %msg, ptr %ms, align 8
                  call void @jnative_exception_throw(ptr %os)
                  ret void
                }

                define ptr @jnative_net_new_socket(i64 %handle) {
                entry:
                  %obj = call ptr @jnative_alloc_zeroed(i64 24)
                  store ptr @meta.TcpSocket, ptr %obj, align 8
                  %hp = getelementptr i8, ptr %obj, i64 8
                  store i64 %handle, ptr %hp, align 8
                  %cp = getelementptr i8, ptr %obj, i64 16
                  store i8 0, ptr %cp, align 1
                  ret ptr %obj
                }

                define ptr @jnative_net_new_server(i64 %handle) {
                entry:
                  %obj = call ptr @jnative_alloc_zeroed(i64 24)
                  store ptr @meta.TcpServer, ptr %obj, align 8
                  %hp = getelementptr i8, ptr %obj, i64 8
                  store i64 %handle, ptr %hp, align 8
                  %cp = getelementptr i8, ptr %obj, i64 16
                  store i8 0, ptr %cp, align 1
                  ret ptr %obj
                }

                define i64 @jnative_net_require_handle(ptr %sock) {
                entry:
                  call void @jnative_null_check(ptr %sock)
                  %cp = getelementptr i8, ptr %sock, i64 16
                  %closed = load i8, ptr %cp, align 1
                  %isClosed = icmp ne i8 %closed, 0
                  br i1 %isClosed, label %fail, label %ok
                fail:
                  %eb = alloca [64 x i8], align 1
                  %ebp = getelementptr [64 x i8], ptr %eb, i64 0, i64 0
                  call void @jnative_throw_net(i32 6, ptr @.jn.net.closed)
                  ret i64 -1
                ok:
                  %hp = getelementptr i8, ptr %sock, i64 8
                  %h = load i64, ptr %hp, align 8
                  ret i64 %h
                }

                """);
    }

    private void emitConnect() {
        sb.append("""
                define ptr @jnative_tcp_connect(ptr %host, i32 %port) {
                entry:
                  %r = call ptr @jnative_tcp_connect_timeout(ptr %host, i32 %port, i32 -1)
                  ret ptr %r
                }

                define ptr @jnative_tcp_connect_timeout(ptr %host, i32 %port, i32 %timeoutMs) {
                entry:
                  call void @jnative_null_check(ptr %host)
                  %badLo = icmp slt i32 %port, 0
                  %badHi = icmp sgt i32 %port, 65535
                  %bad = or i1 %badLo, %badHi
                  br i1 %bad, label %badport, label %go
                badport:
                  call void @jnative_throw_net(i32 5, ptr @.jn.net.badport)
                  ret ptr null
                go:
                  %cstr = call ptr @jnative_ffi_string_to_cstr(ptr %host)
                  %handleSlot = alloca i64, align 8
                  store i64 -1, ptr %handleSlot, align 8
                  %err = alloca [256 x i8], align 1
                  %errp = getelementptr [256 x i8], ptr %err, i64 0, i64 0
                  store i8 0, ptr %errp, align 1
                  call void @jn_enter_native()
                  %rc = call i32 @jnative_plat_tcp_connect(ptr %cstr, i32 %port, i32 %timeoutMs, ptr %handleSlot, ptr %errp, i64 256)
                  call void @jn_leave_native()
                  call void @jnative_ffi_cstr_free(ptr %cstr)
                  %ok = icmp eq i32 %rc, 0
                  br i1 %ok, label %success, label %fail
                fail:
                  call void @jnative_throw_net(i32 %rc, ptr %errp)
                  ret ptr null
                success:
                  %h = load i64, ptr %handleSlot, align 8
                  %obj = call ptr @jnative_net_new_socket(i64 %h)
                  ret ptr %obj
                }

                """);
    }

    private void emitListen() {
        sb.append("""
                define ptr @jnative_tcp_listen(ptr %host, i32 %port) {
                entry:
                  call void @jnative_null_check(ptr %host)
                  %badLo = icmp slt i32 %port, 0
                  %badHi = icmp sgt i32 %port, 65535
                  %bad = or i1 %badLo, %badHi
                  br i1 %bad, label %badport, label %go
                badport:
                  call void @jnative_throw_net(i32 5, ptr @.jn.net.badport)
                  ret ptr null
                go:
                  %cstr = call ptr @jnative_ffi_string_to_cstr(ptr %host)
                  %handleSlot = alloca i64, align 8
                  store i64 -1, ptr %handleSlot, align 8
                  %err = alloca [256 x i8], align 1
                  %errp = getelementptr [256 x i8], ptr %err, i64 0, i64 0
                  store i8 0, ptr %errp, align 1
                  call void @jn_enter_native()
                  %rc = call i32 @jnative_plat_tcp_listen(ptr %cstr, i32 %port, ptr %handleSlot, ptr %errp, i64 256)
                  call void @jn_leave_native()
                  call void @jnative_ffi_cstr_free(ptr %cstr)
                  %ok = icmp eq i32 %rc, 0
                  br i1 %ok, label %success, label %fail
                fail:
                  call void @jnative_throw_net(i32 %rc, ptr %errp)
                  ret ptr null
                success:
                  %h = load i64, ptr %handleSlot, align 8
                  %obj = call ptr @jnative_net_new_server(i64 %h)
                  ret ptr %obj
                }

                define ptr @jnative_tcp_listen_any(i32 %port) {
                entry:
                  %host = call ptr @jnative_string_from_literal(ptr @.jn.net.any, i32 7, ptr @meta.String)
                  %r = call ptr @jnative_tcp_listen(ptr %host, i32 %port)
                  ret ptr %r
                }

                """);
    }

    private void emitAccept() {
        sb.append("""
                define ptr @jnative_tcp_accept(ptr %server) {
                entry:
                  %h = call i64 @jnative_net_require_handle(ptr %server)
                  %handleSlot = alloca i64, align 8
                  store i64 -1, ptr %handleSlot, align 8
                  %err = alloca [256 x i8], align 1
                  %errp = getelementptr [256 x i8], ptr %err, i64 0, i64 0
                  store i8 0, ptr %errp, align 1
                  call void @jn_enter_native()
                  %rc = call i32 @jnative_plat_tcp_accept(i64 %h, ptr %handleSlot, ptr %errp, i64 256)
                  call void @jn_leave_native()
                  %ok = icmp eq i32 %rc, 0
                  br i1 %ok, label %success, label %fail
                fail:
                  call void @jnative_throw_net(i32 %rc, ptr %errp)
                  ret ptr null
                success:
                  %ch = load i64, ptr %handleSlot, align 8
                  %obj = call ptr @jnative_net_new_socket(i64 %ch)
                  ret ptr %obj
                }

                """);
    }

    private void emitRead() {
        sb.append("""
                define i32 @jnative_tcp_read(ptr %sock, ptr %buffer) {
                entry:
                  call void @jnative_null_check(ptr %buffer)
                  %h = call i64 @jnative_net_require_handle(ptr %sock)
                  %len = call i32 @jnative_array_length(ptr %buffer)
                  %base = getelementptr i8, ptr %buffer, i64 16
                  %nSlot = alloca i32, align 4
                  store i32 0, ptr %nSlot, align 4
                  %err = alloca [256 x i8], align 1
                  %errp = getelementptr [256 x i8], ptr %err, i64 0, i64 0
                  store i8 0, ptr %errp, align 1
                  call void @jn_enter_native()
                  %rc = call i32 @jnative_plat_tcp_read(i64 %h, ptr %base, i32 %len, ptr %nSlot, ptr %errp, i64 256)
                  call void @jn_leave_native()
                  %ok = icmp eq i32 %rc, 0
                  br i1 %ok, label %success, label %fail
                fail:
                  call void @jnative_throw_net(i32 %rc, ptr %errp)
                  ret i32 -1
                success:
                  %n = load i32, ptr %nSlot, align 4
                  ret i32 %n
                }

                define ptr @jnative_tcp_read_bytes(ptr %sock, i32 %max) {
                entry:
                  %h = call i64 @jnative_net_require_handle(ptr %sock)
                  %neg = icmp slt i32 %max, 0
                  br i1 %neg, label %bad, label %go
                bad:
                  call void @jnative_throw_net(i32 5, ptr @.jn.net.badport)
                  ret ptr null
                go:
                  %tmp = call ptr @malloc(i64 0)
                  %max64 = sext i32 %max to i64
                  %buf = call ptr @malloc(i64 %max64)
                  %isnull = icmp eq ptr %buf, null
                  br i1 %isnull, label %oom, label %read
                oom:
                  call void @free(ptr %tmp)
                  %err0 = alloca [32 x i8], align 1
                  %err0p = getelementptr [32 x i8], ptr %err0, i64 0, i64 0
                  store i8 0, ptr %err0p, align 1
                  call void @jnative_throw_net(i32 4, ptr %err0p)
                  ret ptr null
                read:
                  call void @free(ptr %tmp)
                  %nSlot = alloca i32, align 4
                  store i32 0, ptr %nSlot, align 4
                  %err = alloca [256 x i8], align 1
                  %errp = getelementptr [256 x i8], ptr %err, i64 0, i64 0
                  store i8 0, ptr %errp, align 1
                  call void @jn_enter_native()
                  %rc = call i32 @jnative_plat_tcp_read(i64 %h, ptr %buf, i32 %max, ptr %nSlot, ptr %errp, i64 256)
                  call void @jn_leave_native()
                  %ok = icmp eq i32 %rc, 0
                  br i1 %ok, label %success, label %fail
                fail:
                  call void @free(ptr %buf)
                  call void @jnative_throw_net(i32 %rc, ptr %errp)
                  ret ptr null
                success:
                  %n = load i32, ptr %nSlot, align 4
                  %arr = call ptr @jnative_array_alloc(i64 1, i32 %n, ptr @meta.array.i8)
                  %dst = getelementptr i8, ptr %arr, i64 16
                  %n64 = sext i32 %n to i64
                  call ptr @memcpy(ptr %dst, ptr %buf, i64 %n64)
                  call void @free(ptr %buf)
                  ret ptr %arr
                }

                define ptr @jnative_tcp_read_all_bytes(ptr %sock) {
                entry:
                  %h = call i64 @jnative_net_require_handle(ptr %sock)
                  %bufSlot = alloca ptr, align 8
                  store ptr null, ptr %bufSlot, align 8
                  %nSlot = alloca i32, align 4
                  store i32 0, ptr %nSlot, align 4
                  %err = alloca [256 x i8], align 1
                  %errp = getelementptr [256 x i8], ptr %err, i64 0, i64 0
                  store i8 0, ptr %errp, align 1
                  call void @jn_enter_native()
                  %rc = call i32 @jnative_plat_tcp_read_all(i64 %h, ptr %bufSlot, ptr %nSlot, ptr %errp, i64 256)
                  call void @jn_leave_native()
                  %ok = icmp eq i32 %rc, 0
                  br i1 %ok, label %success, label %fail
                fail:
                  call void @jnative_throw_net(i32 %rc, ptr %errp)
                  ret ptr null
                success:
                  %n = load i32, ptr %nSlot, align 4
                  %src = load ptr, ptr %bufSlot, align 8
                  %arr = call ptr @jnative_array_alloc(i64 1, i32 %n, ptr @meta.array.i8)
                  %dst = getelementptr i8, ptr %arr, i64 16
                  %n64 = sext i32 %n to i64
                  %has = icmp ne ptr %src, null
                  br i1 %has, label %copy, label %done
                copy:
                  call ptr @memcpy(ptr %dst, ptr %src, i64 %n64)
                  call void @free(ptr %src)
                  br label %done
                done:
                  ret ptr %arr
                }

                define ptr @jnative_tcp_read_string(ptr %sock, i32 %max) {
                entry:
                  %bytes = call ptr @jnative_tcp_read_bytes(ptr %sock, i32 %max)
                  %pending = call i1 @jnative_exception_pending()
                  br i1 %pending, label %retnull, label %ok
                retnull:
                  ret ptr null
                ok:
                  %len = call i32 @jnative_array_length(ptr %bytes)
                  %src = getelementptr i8, ptr %bytes, i64 16
                  %s = call ptr @jnative_string_from_literal(ptr %src, i32 %len, ptr @meta.String)
                  ret ptr %s
                }

                define ptr @jnative_tcp_read_all_string(ptr %sock) {
                entry:
                  %bytes = call ptr @jnative_tcp_read_all_bytes(ptr %sock)
                  %pending = call i1 @jnative_exception_pending()
                  br i1 %pending, label %retnull, label %ok
                retnull:
                  ret ptr null
                ok:
                  %len = call i32 @jnative_array_length(ptr %bytes)
                  %src = getelementptr i8, ptr %bytes, i64 16
                  %s = call ptr @jnative_string_from_literal(ptr %src, i32 %len, ptr @meta.String)
                  ret ptr %s
                }

                """);
    }

    private void emitWrite() {
        sb.append("""
                define void @jnative_tcp_write(ptr %sock, ptr %data) {
                entry:
                  call void @jnative_null_check(ptr %data)
                  %h = call i64 @jnative_net_require_handle(ptr %sock)
                  %len = call i32 @jnative_array_length(ptr %data)
                  %src = getelementptr i8, ptr %data, i64 16
                  %err = alloca [256 x i8], align 1
                  %errp = getelementptr [256 x i8], ptr %err, i64 0, i64 0
                  store i8 0, ptr %errp, align 1
                  call void @jn_enter_native()
                  %rc = call i32 @jnative_plat_tcp_write_all(i64 %h, ptr %src, i32 %len, ptr %errp, i64 256)
                  call void @jn_leave_native()
                  %ok = icmp eq i32 %rc, 0
                  br i1 %ok, label %done, label %fail
                fail:
                  call void @jnative_throw_net(i32 %rc, ptr %errp)
                  ret void
                done:
                  ret void
                }

                define void @jnative_tcp_write_string(ptr %sock, ptr %text) {
                entry:
                  call void @jnative_null_check(ptr %text)
                  %h = call i64 @jnative_net_require_handle(ptr %sock)
                  %len = call i32 @jnative_string_utf8_length(ptr %text)
                  %len64 = sext i32 %len to i64
                  %buf = call ptr @malloc(i64 %len64)
                  %enc = call i32 @jnative_string_encode_utf8(ptr %text, ptr %buf, i32 %len)
                  %err = alloca [256 x i8], align 1
                  %errp = getelementptr [256 x i8], ptr %err, i64 0, i64 0
                  store i8 0, ptr %errp, align 1
                  call void @jn_enter_native()
                  %rc = call i32 @jnative_plat_tcp_write_all(i64 %h, ptr %buf, i32 %len, ptr %errp, i64 256)
                  call void @jn_leave_native()
                  call void @free(ptr %buf)
                  %ok = icmp eq i32 %rc, 0
                  br i1 %ok, label %done, label %fail
                fail:
                  call void @jnative_throw_net(i32 %rc, ptr %errp)
                  ret void
                done:
                  ret void
                }

                """);
    }

    private void emitClose() {
        sb.append("""
                define void @jnative_tcp_close(ptr %sock) {
                entry:
                  call void @jnative_null_check(ptr %sock)
                  %cp = getelementptr i8, ptr %sock, i64 16
                  %closed = load i8, ptr %cp, align 1
                  %isClosed = icmp ne i8 %closed, 0
                  br i1 %isClosed, label %done, label %doClose
                doClose:
                  %hp = getelementptr i8, ptr %sock, i64 8
                  %h = load i64, ptr %hp, align 8
                  call void @jn_enter_native()
                  %ignored = call i32 @jnative_plat_tcp_close(i64 %h)
                  call void @jn_leave_native()
                  store i64 -1, ptr %hp, align 8
                  store i8 1, ptr %cp, align 1
                  br label %done
                done:
                  ret void
                }

                define i1 @jnative_tcp_is_closed(ptr %sock) {
                entry:
                  call void @jnative_null_check(ptr %sock)
                  %cp = getelementptr i8, ptr %sock, i64 16
                  %closed = load i8, ptr %cp, align 1
                  %r = icmp ne i8 %closed, 0
                  ret i1 %r
                }

                define void @jnative_tcp_server_close(ptr %server) {
                entry:
                  call void @jnative_tcp_close(ptr %server)
                  ret void
                }

                define i1 @jnative_tcp_server_is_closed(ptr %server) {
                entry:
                  %r = call i1 @jnative_tcp_is_closed(ptr %server)
                  ret i1 %r
                }

                """);
    }

    private void emitTimeouts() {
        sb.append("""
                define void @jnative_tcp_set_read_timeout(ptr %sock, i32 %ms) {
                entry:
                  %h = call i64 @jnative_net_require_handle(ptr %sock)
                  %err = alloca [256 x i8], align 1
                  %errp = getelementptr [256 x i8], ptr %err, i64 0, i64 0
                  store i8 0, ptr %errp, align 1
                  call void @jn_enter_native()
                  %rc = call i32 @jnative_plat_tcp_set_timeouts(i64 %h, i32 %ms, i32 -1, ptr %errp, i64 256)
                  call void @jn_leave_native()
                  %ok = icmp eq i32 %rc, 0
                  br i1 %ok, label %done, label %fail
                fail:
                  call void @jnative_throw_net(i32 %rc, ptr %errp)
                  ret void
                done:
                  ret void
                }

                define void @jnative_tcp_set_write_timeout(ptr %sock, i32 %ms) {
                entry:
                  %h = call i64 @jnative_net_require_handle(ptr %sock)
                  %err = alloca [256 x i8], align 1
                  %errp = getelementptr [256 x i8], ptr %err, i64 0, i64 0
                  store i8 0, ptr %errp, align 1
                  call void @jn_enter_native()
                  %rc = call i32 @jnative_plat_tcp_set_timeouts(i64 %h, i32 -1, i32 %ms, ptr %errp, i64 256)
                  call void @jn_leave_native()
                  %ok = icmp eq i32 %rc, 0
                  br i1 %ok, label %done, label %fail
                fail:
                  call void @jnative_throw_net(i32 %rc, ptr %errp)
                  ret void
                done:
                  ret void
                }

                """);
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
