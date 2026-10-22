/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.stdlib;

import io.jnative.compiler.SourceUnit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Database module selection must pull CryptoLib (BitOps) without user-side BitOps refs.
 *
 * @author 张晓达
 */
public class StdlibDatabaseBitOpsTest {

    @Test
    void sqliteSourcePullsCryptoForBitOps() throws Exception {
        String src = """
                public class MiniSqlite {
                    public static void main(String[] args) {
                        Database db = Database.connect("sqlite:mem.db");
                        System.out.println(db != null ? 1 : 0);
                    }
                }
                """;
        assertTrue(StdlibIncluder.needsDatabase(src));
        List<SourceUnit> units = StdlibIncluder.collectStdlibSourceUnits(src);
        assertTrue(units.stream().anyMatch(u -> u.path().contains("CryptoLib.java")),
                () -> units.stream().map(SourceUnit::path).toList().toString());
        assertTrue(units.stream().anyMatch(u -> u.path().contains("DbLib.java")));
        assertTrue(units.stream().anyMatch(u -> u.path().contains("PgDriverLib.java")),
                "Pg driver is part of DB stack and references BitOps");
    }
}
