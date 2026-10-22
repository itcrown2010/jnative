/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.backend.wasm;

import io.jnative.ir.IrFunction;
import io.jnative.ir.IrModule;
import io.jnative.ir.IrType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Keep Node/test host imports aligned with WAT emission.
 *
 * @author 张晓达
 */
public class WasmHostImportContractTest {

    @Test
    void emittedWatImportsMatchContract() {
        IrModule module = new IrModule("t");
        IrFunction fn = new IrFunction("jn_main", IrType.VOID, List.of(), true);
        module.addFunction(fn);
        String wat = new BrowserWatEmitter().emit(module);
        List<String> found = new ArrayList<>();
        Matcher m = Pattern.compile("\\(import \"jnative\" \"([^\"]+)\"").matcher(wat);
        while (m.find()) {
            found.add(m.group(1));
        }
        assertEquals(WasmHostImportContract.JNATIVE_IMPORTS, found,
                "Update WasmHostImportContract.JNATIVE_IMPORTS when BrowserWatEmitter.emitImports changes");
        assertTrue(found.contains("unregister_callback"));
    }
}
