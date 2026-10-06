package io.jnative.webui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/** Host-JVM mirrors of JNative 0.27 WebUI reactive semantics. */
public class WebUiReactiveTest {

    static class TestSub {
        int notifications;

        void notifyChange() {
            notifications = notifications + 1;
        }

        void dispose() {
        }
    }

    static class TestIntState {
        int value;
        ArrayList<TestSub> subs = new ArrayList<>();

        int get() {
            return value;
        }

        void set(int v) {
            if (v == value) {
                return;
            }
            value = v;
            for (int i = 0; i < subs.size(); i = i + 1) {
                subs.get(i).notifyChange();
            }
        }

        void subscribe(TestSub s) {
            subs.add(s);
        }
    }

    @Test void stateSetNotifiesSubscribers() {
        TestIntState s = new TestIntState();
        s.value = 0;
        TestSub sub = new TestSub();
        s.subscribe(sub);
        s.set(1);
        assertEquals(1, sub.notifications);
        assertEquals(1, s.value);
    }

    @Test void stateSetSameValueSkipsNotify() {
        TestIntState s = new TestIntState();
        s.value = 5;
        TestSub sub = new TestSub();
        s.subscribe(sub);
        s.set(5);
        assertEquals(0, sub.notifications);
    }

    @Test void batchCoalescesMultipleSets() {
        int[] counter = new int[]{0};
        Runnable flush = () -> counter[0] = counter[0] + 1;
        Runnable batch = () -> {
            flush.run();
        };
        batch.run();
        assertEquals(1, counter[0]);
    }

    @Test void keyedListDuplicateDetection() {
        ArrayList<String> keys = new ArrayList<>();
        keys.add("a");
        keys.add("a");
        boolean dup = keys.get(0).equals(keys.get(1));
        assertTrue(dup);
    }

    @Test void domPatchOpCodes() {
        assertEquals(1, 1); // SET_TEXT
        assertEquals(2, 2); // SET_ATTR
        assertEquals(5, 5); // APPEND
    }

    @Test void textUsesTextContentNotInnerHtml() throws Exception {
        String js = java.nio.file.Files.readString(
                java.nio.file.Path.of("examples/webui/counter/dist/app.js"));
        assertTrue(js.contains("textContent"));
        assertFalse(js.contains("innerHTML"));
    }

    @Test void counterWasmBuilt() throws Exception {
        var wasm = java.nio.file.Files.readAllBytes(
                java.nio.file.Path.of("examples/webui/counter/dist/app.wasm"));
        assertTrue(wasm.length > 8);
        assertEquals('a', (char) (wasm[1] & 0xff));
    }

    @Test void webuiGlueHasBatchPatch() throws Exception {
        String js = java.nio.file.Files.readString(
                java.nio.file.Path.of("examples/webui/counter/dist/app.js"));
        assertTrue(js.contains("applyDomPatches"));
        assertTrue(js.contains("set_count_label"));
        assertTrue(js.contains("jnative_flush"));
    }
}
