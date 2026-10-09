# GC verifier

作者：张晓达

`JNATIVE_GC_VERIFY=1` enables post-minor checks:

- No root slot points into the abandoned from-space
- (Extensible) metadata / generation sanity

Soft stress (`JNATIVE_GC_STRESS=1`) shrinks the nursery for frequent minors without per-allocation STW.
