# Changelog Documentation Policy

Every release (or user-visible merge train) should record:

| Section | Contents |
|---------|----------|
| **Added** | New language features, APIs, tools, examples, docs pages |
| **Changed** | Behavior or signature changes that are compatible |
| **Fixed** | Bug fixes with short reproduction hints |
| **Breaking Changes** | Removals, incompatible signature/target changes |
| **Documentation** | Doc-only updates that change public guidance |
| **Examples** | New or repaired runnable samples |

## Feature workflow

When adding a feature:

1. Implementation  
2. Test  
3. Example  
4. API / language documentation  
5. Tutorial (if user-facing journey changes)  
6. Changelog entry  

Do not mark a capability **Stable** in the matrix until steps 1–4 are done on at least one verified host.
