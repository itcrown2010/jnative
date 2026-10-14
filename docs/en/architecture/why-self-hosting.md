# Why Self-Hosting

Self-hosting validates that JNative is expressive enough to implement serious compiler infrastructure, reduces long-term dependence on a second implementation language for *compiler sources*, and creates a clear ecosystem milestone.

## What it does **not** automatically mean

- Better runtime performance  
- Better language design  
- More users  
- Higher compiler quality without tests  

## Approach here

Correctness and **compatibility with the reference lexer/parser** come before replacement. LLVM as a backend remains acceptable. Self-hosting means the language can reproduce its compiler — not that every low-level component must be rewritten in JNative.
