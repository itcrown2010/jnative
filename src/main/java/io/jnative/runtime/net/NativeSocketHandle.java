/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.runtime.net;

/**
 * Opaque OS socket handle (POSIX {@code int} fd or Windows {@code SOCKET}).
 *
 * <p>Not a public JNative language type. Runtime stores the platform value as
 * {@code i64} inside {@code TcpSocket}/{@code TcpServer}; the language API never
 * exposes the raw handle. Do not assume {@code handle == int} at the language layer.
 * @author 张晓达
 */
public final class NativeSocketHandle {
    private NativeSocketHandle() {
    }
}
