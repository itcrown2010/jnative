// JNative boxed primitives (Integer / Long / Boolean; Float / Double thin).
// valueOf caches match Java identity for Integer/Long in [-128, 127] and Boolean TRUE/FALSE.
// Autobox/unbox is applied by the compiler; null unbox → NullPointerException.

/**
 * @author 张晓达
 */
class Integer {
    static Integer[] cache;
    static int cacheReady;
    int value;

    Integer(int value) {
        this.value = value;
    }

    static void ensureCache() {
        if (cacheReady != 0) {
            return;
        }
        Integer[] c = new Integer[256];
        int i = 0;
        while (i < 256) {
            c[i] = new Integer(i - 128);
            i = i + 1;
        }
        cache = c;
        cacheReady = 1;
    }

    static Integer valueOf(int i) {
        if (i >= -128) {
            if (i <= 127) {
                ensureCache();
                return cache[i + 128];
            }
        }
        return new Integer(i);
    }

    int intValue() {
        return this.value;
    }

    long longValue() {
        return (long) this.value;
    }

    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (other == null) {
            return false;
        }
        if (!(other instanceof Integer)) {
            return false;
        }
        Integer o = (Integer) other;
        return this.value == o.value;
    }

    public int hashCode() {
        return this.value;
    }
}

class Long {
    static Long[] cache;
    static int cacheReady;
    long value;

    Long(long value) {
        this.value = value;
    }

    static void ensureCache() {
        if (cacheReady != 0) {
            return;
        }
        Long[] c = new Long[256];
        int i = 0;
        while (i < 256) {
            c[i] = new Long((long) (i - 128));
            i = i + 1;
        }
        cache = c;
        cacheReady = 1;
    }

    static Long valueOf(long i) {
        if (i >= -128L) {
            if (i <= 127L) {
                ensureCache();
                return cache[(int) (i + 128L)];
            }
        }
        return new Long(i);
    }

    long longValue() {
        return this.value;
    }

    int intValue() {
        return (int) this.value;
    }

    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (other == null) {
            return false;
        }
        if (!(other instanceof Long)) {
            return false;
        }
        Long o = (Long) other;
        return this.value == o.value;
    }

    public int hashCode() {
        return (int) (this.value ^ (this.value >>> 32));
    }
}

class Boolean {
    static Boolean TRUE = new Boolean(true);
    static Boolean FALSE = new Boolean(false);
    boolean value;

    Boolean(boolean value) {
        this.value = value;
    }

    static Boolean valueOf(boolean b) {
        if (b) {
            return TRUE;
        }
        return FALSE;
    }

    boolean booleanValue() {
        return this.value;
    }

    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (other == null) {
            return false;
        }
        if (!(other instanceof Boolean)) {
            return false;
        }
        Boolean o = (Boolean) other;
        return this.value == o.value;
    }

    public int hashCode() {
        if (this.value) {
            return 1231;
        }
        return 1237;
    }
}

class Float {
    float value;

    Float(float value) {
        this.value = value;
    }

    static Float valueOf(float f) {
        return new Float(f);
    }

    float floatValue() {
        return this.value;
    }

    double doubleValue() {
        return (double) this.value;
    }

    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (other == null) {
            return false;
        }
        if (!(other instanceof Float)) {
            return false;
        }
        Float o = (Float) other;
        return this.value == o.value;
    }

    public int hashCode() {
        // Not bit-identical to Java floatToIntBits; adequate for map keys of equal values.
        return (int) this.value;
    }
}

class Double {
    double value;

    Double(double value) {
        this.value = value;
    }

    static Double valueOf(double d) {
        return new Double(d);
    }

    double doubleValue() {
        return this.value;
    }

    float floatValue() {
        return (float) this.value;
    }

    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (other == null) {
            return false;
        }
        if (!(other instanceof Double)) {
            return false;
        }
        Double o = (Double) other;
        return this.value == o.value;
    }

    public int hashCode() {
        long bits = (long) this.value;
        return (int) bits;
    }
}
