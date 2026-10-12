/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.mobile;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Node harness for bridge.hello / capability / timeout after template generation.
 * @author 张晓达
 */
public class MobileBridgeHandshakeTest {

    @TempDir
    Path parent;

    @Test
    void handshakeAndCapabilityErrors() throws Exception {
        Assumptions.assumeTrue(which("node"));
        Path dir = MobileProjectGenerator.generate(parent, new MobileProjectGenerator.Options(
                "BridgeDemo", "Bridge Demo", "io.jnative.bridge.demo",
                "io.jnative.bridge.demo", "0.1.0", "1"));
        Path bridge = dir.resolve("web/bridge.js");
        Path test = parent.resolve("handshake.mjs");
        Files.writeString(test, """
                import { JNativeBridge, installWebPlatformHost } from '%s';

                const listeners = new Map();
                globalThis.window = {
                  location: { origin: 'http://127.0.0.1' },
                  addEventListener(type, fn) {
                    if (!listeners.has(type)) listeners.set(type, new Set());
                    listeners.get(type).add(fn);
                  },
                  removeEventListener(type, fn) { listeners.get(type)?.delete(fn); },
                  postMessage(data, origin) {
                    const ev = { data, origin: origin || 'http://127.0.0.1' };
                    for (const fn of listeners.get('message') || []) fn(ev);
                  },
                };
                globalThis.document = { addEventListener() {}, hidden: false };
                globalThis.localStorage = {
                  _m: new Map(),
                  getItem(k) { return this._m.has(k) ? this._m.get(k) : null; },
                  setItem(k, v) { this._m.set(k, String(v)); },
                  removeItem(k) { this._m.delete(k); },
                };

                installWebPlatformHost();
                const bridge = new JNativeBridge({
                  requiredCapabilities: ['platform.getName', 'storage.get'],
                });
                const session = await bridge.init();
                if (session.platform !== 'web' || session.version !== 1) throw new Error('bad session');
                // second init should reuse / succeed
                const again = await bridge.init();
                if (again.platform !== 'web') throw new Error('re-init failed');

                let missing = false;
                try {
                  await new JNativeBridge({ requiredCapabilities: ['not.a.cap'] }).init();
                } catch (e) {
                  missing = e.code === 'CAPABILITY_UNAVAILABLE';
                }
                if (!missing) throw new Error('expected CAPABILITY_UNAVAILABLE');

                bridge.destroy();
                let destroyed = false;
                try { await bridge.call('platform.getName'); } catch (e) { destroyed = e.code === 'DESTROYED'; }
                if (!destroyed) throw new Error('expected DESTROYED');
                console.log('OK');
                """.formatted(bridge.toAbsolutePath().toString().replace("\\", "\\\\")),
                StandardCharsets.UTF_8);

        Process p = new ProcessBuilder("node", test.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p.waitFor(), out);
        assertTrue(out.contains("OK"), out);
    }

    private static boolean which(String cmd) throws Exception {
        Process p = new ProcessBuilder("which", cmd).start();
        return p.waitFor() == 0;
    }
}
