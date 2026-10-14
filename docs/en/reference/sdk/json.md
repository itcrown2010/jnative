# JSON API

**Status:** Experimental · **Source:** `stdlib/json/JsonLib.java`

## Overview

DOM-style JSON values with parse/stringify.

## Types

| Type | Kind |
|------|------|
| `JsonValue` | Base |
| `JsonNull` | null |
| `JsonBoolean` | bool |
| `JsonNumber` | number |
| `JsonString` | string |
| `JsonArray` | array (`add`, `size`, `get`) |
| `JsonObject` | object (`put`, `putString`, `putNumber`, `has`, `get`, …) |
| `Json` | facade |

## Methods

### `Json`

| Name | Signature |
|------|-----------|
| `parse` | `static JsonValue parse(String text)` |
| `stringify` | `static String stringify(JsonValue v)` |

### `JsonObject` (selected)

| Name | Signature |
|------|-----------|
| `put` | `void put(String key, JsonValue value)` |
| `putString` | `void putString(String key, String value)` |
| `putNumber` | `void putNumber(String key, double value)` |
| `has` | `boolean has(String key)` |
| `get` | `JsonValue get(String key)` |

See source for getters that cast to string/number.

## Examples

`examples/json-demo/` · recipe [parse-json](../../recipes/parse-json.md)

## Error Handling

Parse errors come from `JsonParser` — treat invalid input as exceptional.

## Thread Safety

Not synchronized; do not mutate shared `JsonObject` graphs across threads without locking.

## Platform Notes

Available wherever stdlib JSON is linked (native + typically wasm if included).

## Known Limitations

- Not Jackson/Gson annotations  
- Numbers are double-oriented in `JsonNumber`  
