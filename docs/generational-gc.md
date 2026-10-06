# Generational GC

## Minor GC (Cheney)

1. STW + retire TLABs + fill waste
2. Forward roots / remset / Cheney scan to-space
3. Promote when `age >= JNATIVE_PROMOTION_AGE`
4. Rescan old/large for young refs; finish Cheney
5. Swap semi-spaces; verify abandoned space empty (`JNATIVE_GC_VERIFY`)

## Major GC

Minor evacuate, then mark-sweep old + large from all roots (including young survivors).

`System.gc()` triggers major/full collection.
