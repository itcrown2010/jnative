/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

import java.util.List;

/** One switch section: one or more labels followed by statements (fall-through allowed for
 * {@code case X:}; arrow sections {@code case X ->} never fall through).
 * @author 张晓达
 */
public record SwitchSection(
        List<SwitchLabel> labels,
        List<Statement> statements,
        boolean arrow,
        SourceLocation location
) {
    public SwitchSection(List<SwitchLabel> labels, List<Statement> statements, SourceLocation location) {
        this(labels, statements, false, location);
    }
}
