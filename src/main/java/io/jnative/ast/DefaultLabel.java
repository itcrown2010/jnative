package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/**
 * @author 张晓达
 */
public record DefaultLabel(SourceLocation location) implements SwitchLabel {
}
