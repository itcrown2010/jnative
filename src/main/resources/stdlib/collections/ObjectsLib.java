/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

// JNative java.util.Objects subset. Objects.hash takes 1..6 arguments (no varargs yet) and uses
// the same 31-multiplier formula as Arrays.hashCode(Object[]).

class Objects {
    static boolean equals(Object a, Object b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.equals(b);
    }

    static int hashCode(Object o) {
        if (o == null) {
            return 0;
        }
        return o.hashCode();
    }

    static int h1(Object o) {
        if (o == null) {
            return 0;
        }
        return o.hashCode();
    }

    static int hash(Object a) {
        return 31 + h1(a);
    }

    static int hash(Object a, Object b) {
        return 31 * hash(a) + h1(b);
    }

    static int hash(Object a, Object b, Object c) {
        return 31 * hash(a, b) + h1(c);
    }

    static int hash(Object a, Object b, Object c, Object d) {
        return 31 * hash(a, b, c) + h1(d);
    }

    static int hash(Object a, Object b, Object c, Object d, Object e) {
        return 31 * hash(a, b, c, d) + h1(e);
    }

    static int hash(Object a, Object b, Object c, Object d, Object e, Object f) {
        return 31 * hash(a, b, c, d, e) + h1(f);
    }

    static String toString(Object o) {
        if (o == null) {
            return "null";
        }
        return o.toString();
    }

    static String toString(Object o, String nullDefault) {
        if (o == null) {
            return nullDefault;
        }
        return o.toString();
    }

    static boolean isNull(Object o) {
        return o == null;
    }

    static boolean nonNull(Object o) {
        return o != null;
    }

    static <T> T requireNonNull(T o) {
        if (o == null) {
            throw new NullPointerException();
        }
        return o;
    }

    static <T> T requireNonNull(T o, String message) {
        if (o == null) {
            throw new NullPointerException(message);
        }
        return o;
    }

    static <T> T requireNonNullElse(T o, T defaultObj) {
        if (o != null) {
            return o;
        }
        if (defaultObj == null) {
            throw new NullPointerException("defaultObj");
        }
        return defaultObj;
    }

    static int checkIndex(int index, int length) {
        if (index < 0 || index >= length) {
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for length " + length);
        }
        return index;
    }
}
