/**
 * Shared helpers for the B1 layer: row→JSON, JSON body field access, parameter building.
 * @author 张晓达
 */
class ErpB1Util {
    static String camel(String snake) {
        if (snake == null) {
            return "";
        }
        String out = "";
        int up = 0;
        int i = 0;
        while (i < snake.length()) {
            int c = snake.charAt(i);
            if (c == 95) {
                up = 1;
            } else {
                String ch = snake.substring(i, i + 1);
                if (up != 0 && c >= 97 && c <= 122) {
                    out = out + "ABCDEFGHIJKLMNOPQRSTUVWXYZ".substring(c - 97, c - 96);
                } else {
                    out = out + ch;
                }
                up = 0;
            }
            i = i + 1;
        }
        return out;
    }

    /** Spec entries are separated by '|'; each is "expr[ AS alias]:type" with type i (integer) or s (text). */
    static String specEntry(String spec, int n) {
        String rest = spec;
        int i = 0;
        while (i < n) {
            int k = rest.indexOf("|");
            if (k < 0) {
                return "";
            }
            rest = rest.substring(k + 1, rest.length());
            i = i + 1;
        }
        int k2 = rest.indexOf("|");
        if (k2 >= 0) {
            return rest.substring(0, k2);
        }
        return rest;
    }

    static String specType(String ent) {
        return ent.substring(ent.length() - 1, ent.length());
    }

    static String specExpr(String ent) {
        return ent.substring(0, ent.length() - 2);
    }

    static String specKey(String ent) {
        String e = specExpr(ent);
        int a = e.indexOf(" AS ");
        if (a >= 0) {
            return camel(e.substring(a + 4, e.length()));
        }
        int d = e.indexOf(".");
        if (d >= 0) {
            return camel(e.substring(d + 1, e.length()));
        }
        return camel(e);
    }

    static String specSelect(String spec) {
        String out = "";
        int n = 0;
        while (specEntry(spec, n).length() > 0) {
            if (n > 0) {
                out = out + ", ";
            }
            out = out + specExpr(specEntry(spec, n));
            n = n + 1;
        }
        return out;
    }

    static String rowJsonSpec(Row row, String spec) {
        String out = "{";
        int n = 0;
        while (specEntry(spec, n).length() > 0) {
            String en = specEntry(spec, n);
            if (n > 0) {
                out = out + ",";
            }
            out = out + "\"" + specKey(en) + "\":";
            if (row.isNull(n) != 0) {
                out = out + "null";
            } else if (specType(en).equals("i")) {
                out = out + CryptoParse.longToString(row.getLong(n));
            } else {
                out = out + ErpJson.str(row.getString(n));
            }
            n = n + 1;
        }
        return out + "}";
    }

    static String rowsJsonSpec(DbQueryResult r, String spec) {
        String out = "[";
        int i = 0;
        while (i < r.rows.size()) {
            if (i > 0) {
                out = out + ",";
            }
            Row rw = r.rows.get(i);
            String one = rowJsonSpec(rw, spec);
            out = out + one;
            i = i + 1;
        }
        return out + "]";
    }

    static String rowJson(Row row) {
        String out = "{";
        int i = 0;
        while (i < row.columnCount()) {
            if (i > 0) {
                out = out + ",";
            }
            out = out + "\"" + camel(row.columnName(i)) + "\":";
            if (row.isNull(i) != 0) {
                out = out + "null";
            } else if (row.columnTypes[i] == DbColumnType.INTEGER) {
                out = out + CryptoParse.longToString(row.getLong(i));
            } else if (row.columnTypes[i] == DbColumnType.FLOAT) {
                out = out + CryptoParse.longToString(row.getLong(i));
            } else {
                out = out + ErpJson.str(row.getString(i));
            }
            i = i + 1;
        }
        return out + "}";
    }

    static String rowsJson(DbQueryResult r) {
        String out = "[";
        int i = 0;
        while (i < r.rows.size()) {
            if (i > 0) {
                out = out + ",";
            }
            Row rw = r.rows.get(i);
            String one = rowJson(rw);
            out = out + one;
            i = i + 1;
        }
        return out + "]";
    }

    static String text(JsonObject o, String key, String def) {
        JsonValue v = o.get(key);
        if (v == null || v.isNull()) {
            return def;
        }
        if (v.kind == JsonValue.STRING_KIND) {
            return v.asString();
        }
        if (v.kind == JsonValue.NUMBER_KIND) {
            return CryptoParse.longToString(o.getLong(key, 0L));
        }
        if (v.kind == JsonValue.BOOL_KIND) {
            if (v.asBoolean()) {
                return "1";
            }
            return "0";
        }
        return def;
    }

    static int has(JsonObject o, String key) {
        if (o.has(key) == false) {
            return 0;
        }
        JsonValue v = o.get(key);
        if (v == null || v.isNull()) {
            return 0;
        }
        return 1;
    }

    static String lng(long v) {
        return CryptoParse.longToString(v);
    }

    static ArrayList<Object> params() {
        return new ArrayList<Object>();
    }

    static String safeLike(String q) {
        String s = "";
        int i = 0;
        while (q != null && i < q.length()) {
            int c = q.charAt(i);
            if (c != 37 && c != 95 && c != 39 && c != 92) {
                s = s + q.substring(i, i + 1);
            }
            i = i + 1;
        }
        return s;
    }

    static void err(WebContext ctx, int status, String code, String msg) {
        ErpAuth.err(ctx, status, code, msg);
    }

    static String exMsg(Exception ex, String def) {
        if (ex != null && ex.getMessage() != null && ex.getMessage().length() > 0) {
            return ex.getMessage();
        }
        return def;
    }

    static int isUnique(String m) {
        if (m == null) {
            return 0;
        }
        if (m.indexOf("UNIQUE") >= 0 || m.indexOf("unique") >= 0 || m.indexOf("duplicate") >= 0) {
            return 1;
        }
        return 0;
    }

    static long dayStart(long ms) {
        return ms - (ms % 86400000L);
    }
}
