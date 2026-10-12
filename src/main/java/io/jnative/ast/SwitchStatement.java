/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

import java.util.List;

/** Classic {@code switch} statement (not switch expression / arrow syntax).
 * @author 张晓达
 */
public record SwitchStatement(
        Expression selector,
        List<SwitchSection> sections,
        SourceLocation location
) implements Statement {
}
