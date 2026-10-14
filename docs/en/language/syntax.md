# Syntax

**Status:** Implemented

## Concept

Source is UTF-8 text organised into compilation units: optional `package`, `import` lines, then type declarations.

## Comments & identifiers

```java
// line comment
/* block comment */
```

Identifiers follow Java-like rules (`Main`, `taskId`). Keywords include `class`, `interface`, `extends`, `implements`, `static`, `public`, `private`, `protected`, `if`, `else`, `while`, `for`, `return`, `new`, `try`, `catch`, `finally`, `throw`, `null`, `true`, `false`, and related words used by the parser.

## Imports

Explicit type imports only:

```java
import jnative.json.Json;
```

**Not supported:** `import foo.*`, static imports.

## Example project unit

```java
public class Main {
    public static void main(String[] args) {
        System.out.println("ok");
    }
}
```
