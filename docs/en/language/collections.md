# Collections (language + stdlib)

**Status:** Experimental · **Source:** `stdlib/collections/CollectionsLib.java` · **Tests:** `CollectionsEndToEndTest`

## What it is

Generic collections such as `ArrayList<T>` live in the **SDK**, used with language generics.

## Minimal example

```java
ArrayList<String> names = new ArrayList<String>();
names.add("Ada");
System.out.println(names.size());
```

## Real-world example

CLI Todo tutorial stores tasks in an `ArrayList` then persists with `File`.

## Common mistakes

- Importing `java.util.*` from the JDK — not available.  
- Assuming full JDK collections / streams API.

## Limitations

Not a complete `java.util` port. Prefer APIs exercised in e2e and examples.

## Related

[generics](generics.md) · [sdk/collections](../sdk/collections.md)
