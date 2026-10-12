/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/** {@code case <expr>:} or {@code default:}.
 * @author 张晓达
 */
public sealed interface SwitchLabel permits CaseLabel, DefaultLabel {
    SourceLocation location();
}
