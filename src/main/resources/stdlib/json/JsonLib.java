/**
 * Minimal JSON library (0.21) — pure JNative, no compiler intrinsics.
 */

class JsonValue {
    static int NULL_KIND = 0;
    static int BOOL_KIND = 1;
    static int NUMBER_KIND = 2;
    static int STRING_KIND = 3;
    static int ARRAY_KIND = 4;
    static int OBJECT_KIND = 5;

    int kind;

    JsonValue(int k) {
        kind = k;
    }

    boolean isNull() {
        return kind == NULL_KIND;
    }

    boolean isObject() {
        return kind == OBJECT_KIND;
    }

    boolean isArray() {
        return kind == ARRAY_KIND;
    }

    boolean isString() {
        return kind == STRING_KIND;
    }

    JsonObject asObject() {
        return (JsonObject) this;
    }

    JsonArray asArray() {
        return (JsonArray) this;
    }

    String asString() {
        return ((JsonString) this).value;
    }

    double asNumber() {
        return ((JsonNumber) this).value;
    }

    boolean asBoolean() {
        return ((JsonBoolean) this).value != 0;
    }
}

class JsonNull extends JsonValue {
    JsonNull() {
        super(NULL_KIND);
    }

    static JsonNull get() {
        return new JsonNull();
    }
}

class JsonBoolean extends JsonValue {
    int value;

    JsonBoolean(int v) {
        super(BOOL_KIND);
        value = v;
    }
}

class JsonNumber extends JsonValue {
    double value;

    JsonNumber(double v) {
        super(NUMBER_KIND);
        value = v;
    }
}

class JsonString extends JsonValue {
    String value;

    JsonString(String v) {
        super(STRING_KIND);
        if (v == null) {
            value = "";
        } else {
            value = v;
        }
    }
}

class JsonArray extends JsonValue {
    ArrayList<JsonValue> items;

    JsonArray() {
        super(ARRAY_KIND);
        items = new ArrayList<JsonValue>();
    }

    void add(JsonValue v) {
        items.add(v);
    }

    int size() {
        return items.size();
    }

    JsonValue get(int i) {
        return items.get(i);
    }
}

class JsonObject extends JsonValue {
    ArrayList<String> keys;
    ArrayList<JsonValue> values;

    JsonObject() {
        super(OBJECT_KIND);
        keys = new ArrayList<String>();
        values = new ArrayList<JsonValue>();
    }

    void put(String key, JsonValue value) {
        int i = 0;
        while (i < keys.size()) {
            if (keys.get(i).equals(key)) {
                values.set(i, value);
                return;
            }
            i = i + 1;
        }
        keys.add(key);
        values.add(value);
    }

    void putString(String key, String value) {
        put(key, new JsonString(value));
    }

    void putNumber(String key, double value) {
        put(key, new JsonNumber(value));
    }

    boolean has(String key) {
        int i = 0;
        while (i < keys.size()) {
            if (keys.get(i).equals(key)) {
                return true;
            }
            i = i + 1;
        }
        return false;
    }

    JsonValue get(String key) {
        int i = 0;
        while (i < keys.size()) {
            if (keys.get(i).equals(key)) {
                return values.get(i);
            }
            i = i + 1;
        }
        return JsonNull.get();
    }

    String getString(String key, String def) {
        JsonValue v = get(key);
        if (v.isString()) {
            return v.asString();
        }
        return def;
    }

    /** Narrowing helper for generated codecs — no reflection. */
    int getInt(String key, int def) {
        JsonValue v = get(key);
        if (v == null) {
            return def;
        }
        if (v.kind == JsonValue.NUMBER_KIND) {
            return JsonParseInt.fromNumberString(JsonWriter.write(v), def);
        }
        if (v.isString()) {
            return JsonParseInt.fromNumberString(v.asString(), def);
        }
        return def;
    }

    long getLong(String key, long def) {
        if (!has(key)) {
            return def;
        }
        return getInt(key, 0);
    }

    int size() {
        return keys.size();
    }
}

class JsonParser {
    String src;
    int pos;
    int n;

    JsonParser(String text) {
        if (text == null) {
            src = "";
        } else {
            src = text;
        }
        pos = 0;
        n = src.length();
    }

    static JsonValue parse(String text) {
        JsonParser p = new JsonParser(text);
        JsonValue v = p.parseValue();
        p.skipWs();
        return v;
    }

    void skipWs() {
        int cont = 1;
        while (pos < n && cont != 0) {
            int c = src.charAt(pos);
            if (c == 32 || c == 9 || c == 10 || c == 13) {
                pos = pos + 1;
            } else {
                cont = 0;
            }
        }
    }

    JsonValue parseValue() {
        skipWs();
        if (pos >= n) {
            return JsonNull.get();
        }
        int c = src.charAt(pos);
        if (c == 123) {
            return parseObject();
        }
        if (c == 91) {
            return parseArray();
        }
        if (c == 34) {
            return new JsonString(parseString());
        }
        if (c == 116 || c == 102 || c == 110) {
            return parseLiteral();
        }
        return parseNumber();
    }

    JsonValue parseLiteral() {
        if (match("true") != 0) {
            return new JsonBoolean(1);
        }
        if (match("false") != 0) {
            return new JsonBoolean(0);
        }
        if (match("null") != 0) {
            return JsonNull.get();
        }
        return JsonNull.get();
    }

    int match(String lit) {
        int ln = lit.length();
        if (pos + ln > n) {
            return 0;
        }
        if (src.substring(pos, pos + ln).equals(lit)) {
            pos = pos + ln;
            return 1;
        }
        return 0;
    }

    JsonObject parseObject() {
        JsonObject obj = new JsonObject();
        pos = pos + 1;
        skipWs();
        if (pos < n && src.charAt(pos) == 125) {
            pos = pos + 1;
            return obj;
        }
        int more = 1;
        while (more != 0) {
            skipWs();
            String key = parseString();
            skipWs();
            if (pos < n && src.charAt(pos) == 58) {
                pos = pos + 1;
            }
            JsonValue val = parseValue();
            obj.put(key, val);
            skipWs();
            if (pos < n && src.charAt(pos) == 44) {
                pos = pos + 1;
            } else {
                more = 0;
            }
        }
        skipWs();
        if (pos < n && src.charAt(pos) == 125) {
            pos = pos + 1;
        }
        return obj;
    }

    JsonArray parseArray() {
        JsonArray arr = new JsonArray();
        pos = pos + 1;
        skipWs();
        if (pos < n && src.charAt(pos) == 93) {
            pos = pos + 1;
            return arr;
        }
        int more = 1;
        while (more != 0) {
            arr.add(parseValue());
            skipWs();
            if (pos < n && src.charAt(pos) == 44) {
                pos = pos + 1;
            } else {
                more = 0;
            }
        }
        skipWs();
        if (pos < n && src.charAt(pos) == 93) {
            pos = pos + 1;
        }
        return arr;
    }

    String parseString() {
        if (pos >= n || src.charAt(pos) != 34) {
            return "";
        }
        pos = pos + 1;
        String out = "";
        int cont = 1;
        while (pos < n && cont != 0) {
            int c = src.charAt(pos);
            if (c == 34) {
                pos = pos + 1;
                cont = 0;
            } else if (c == 92 && pos + 1 < n) {
                int n2 = src.charAt(pos + 1);
                if (n2 == 34) {
                    out = out + "\"";
                } else if (n2 == 92) {
                    out = out + "\\";
                } else if (n2 == 110) {
                    out = out + "\n";
                } else if (n2 == 116) {
                    out = out + "\t";
                } else {
                    out = out + src.substring(pos + 1, pos + 2);
                }
                pos = pos + 2;
            } else {
                out = out + src.substring(pos, pos + 1);
                pos = pos + 1;
            }
        }
        return out;
    }

    JsonNumber parseNumber() {
        int start = pos;
        int cont = 1;
        while (pos < n && cont != 0) {
            int c = src.charAt(pos);
            int ok = 0;
            if (c >= 48 && c <= 57) {
                ok = 1;
            }
            if (c == 45 || c == 43 || c == 46 || c == 101 || c == 69) {
                ok = 1;
            }
            if (ok != 0) {
                pos = pos + 1;
            } else {
                cont = 0;
            }
        }
        String lit = src.substring(start, pos);
        double v = JsonUtil.parseDouble(lit);
        return new JsonNumber(v);
    }
}

class JsonUtil {
    static double parseDouble(String s) {
        if (s == null || s.length() == 0) {
            return 0.0;
        }
        int i = 0;
        int sign = 1;
        if (s.charAt(0) == 45) {
            sign = -1;
            i = 1;
        }
        double whole = 0.0;
        int cont = 1;
        while (i < s.length() && cont != 0) {
            int c = s.charAt(i);
            if (c >= 48 && c <= 57) {
                whole = whole * 10.0 + (c - 48);
                i = i + 1;
            } else {
                cont = 0;
            }
        }
        return whole * sign;
    }

    static String escape(String s) {
        if (s == null) {
            return "";
        }
        String out = "";
        int i = 0;
        while (i < s.length()) {
            int c = s.charAt(i);
            if (c == 34) {
                out = out + "\\\"";
            } else if (c == 92) {
                out = out + "\\\\";
            } else if (c == 10) {
                out = out + "\\n";
            } else {
                out = out + s.substring(i, i + 1);
            }
            i = i + 1;
        }
        return out;
    }
}

class JsonWriter {
    static String write(JsonValue v) {
        String out = "";
        int i = 0;
        JsonArray a = null;
        JsonObject o = null;
        if (v == null || v.isNull()) {
            return "null";
        }
        if (v.kind == JsonValue.BOOL_KIND) {
            if (v.asBoolean()) {
                return "true";
            }
            return "false";
        }
        if (v.kind == JsonValue.NUMBER_KIND) {
            return "" + v.asNumber();
        }
        if (v.kind == JsonValue.STRING_KIND) {
            return "\"" + JsonUtil.escape(v.asString()) + "\"";
        }
        if (v.kind == JsonValue.ARRAY_KIND) {
            a = v.asArray();
            out = "[";
            i = 0;
            while (i < a.size()) {
                if (i > 0) {
                    out = out + ",";
                }
                out = out + write(a.get(i));
                i = i + 1;
            }
            return out + "]";
        }
        if (v.kind == JsonValue.OBJECT_KIND) {
            o = v.asObject();
            out = "{";
            i = 0;
            while (i < o.size()) {
                if (i > 0) {
                    out = out + ",";
                }
                out = out + "\"" + JsonUtil.escape(o.keys.get(i)) + "\":";
                out = out + write(o.values.get(i));
                i = i + 1;
            }
            return out + "}";
        }
        return "null";
    }
}

/** Facade over JsonParser / JsonWriter (0.22). */
class Json {
    static String stringify(JsonValue v) {
        return JsonWriter.write(v);
    }

    static JsonValue parse(String text) {
        return JsonParser.parse(text);
    }
}

/**
 * Explicit typed JSON codec — no reflection / no .class (0.22 phase-1).
 */
interface JsonCodec<T> {
    JsonValue encode(T value);

    T decode(JsonValue value);
}

/** Minimal decimal/integer parse for generated JSON codecs (0.32). */
class JsonParseInt {
    static int fromNumberString(String s, int def) {
        if (s == null || s.length() == 0) {
            return def;
        }
        int start = 0;
        int sign = 1;
        if (s.startsWith("-")) {
            sign = -1;
            start = 1;
        }
        int value = 0;
        int i = start;
        int digits = 0;
        int done = 0;
        while (i < s.length() && done == 0) {
            String one = s.substring(i, i + 1);
            if (one.equals(".")) {
                done = 1;
            } else {
                int digit = digitValue(one);
                if (digit < 0) {
                    if (digits == 0) {
                        return def;
                    }
                    done = 1;
                } else {
                    value = value * 10 + digit;
                    digits = digits + 1;
                    i = i + 1;
                }
            }
        }
        if (digits == 0) {
            return def;
        }
        return value * sign;
    }

    static int digitValue(String one) {
        if (one.equals("0")) { return 0; }
        if (one.equals("1")) { return 1; }
        if (one.equals("2")) { return 2; }
        if (one.equals("3")) { return 3; }
        if (one.equals("4")) { return 4; }
        if (one.equals("5")) { return 5; }
        if (one.equals("6")) { return 6; }
        if (one.equals("7")) { return 7; }
        if (one.equals("8")) { return 8; }
        if (one.equals("9")) { return 9; }
        return -1;
    }
}
