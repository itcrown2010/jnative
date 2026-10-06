# Check elimination

## Null checks (`NullCheckEliminationPass`, O1+)

Removes `NullCheck` when the pointer is an alias of a fresh `NewObject`/`NewArray`/`StringLiteral`, or the same pointer was already checked in the block.

## Bounds checks (`BoundsCheckEliminationPass`, O2+)

Marks `LoadElement` with `unchecked=true` in simple counted loops; LLVM emits `@jnative_array_elem_ptr_unchecked`.

## Safepoints (`SafepointEliminationPass`, O2+)

Removes `SafepointPoll` only in functions with `omitSafepoints`/`noGcRegion` or deterministic effect contracts without allocate/safepoint.
