package io.jnative.ir;

public sealed interface IrValue permits IrValue.Temp, IrValue.Param, IrValue.Local, IrValue.Const, IrValue.GlobalString {

    IrType type();

    String name();

    record Temp(int id, IrType type) implements IrValue {
        @Override
        public String name() {
            return "%" + id;
        }
    }

    record Param(String identifier, IrType type) implements IrValue {
        @Override
        public String name() {
            return "%" + identifier;
        }
    }

    /** Stack slot / alloca for mutable locals. */
    record Local(String identifier, IrType type) implements IrValue {
        @Override
        public String name() {
            return "%" + identifier + ".addr";
        }
    }

    record Const(IrType type, Object value) implements IrValue {
        @Override
        public String name() {
            if (type == IrType.DOUBLE) {
                return floatBitsName(((Number) value).doubleValue(), true);
            }
            if (type == IrType.FLOAT) {
                return floatBitsName(((Number) value).floatValue(), false);
            }
            if (type == IrType.I1) {
                return Boolean.TRUE.equals(value) ? "1" : "0";
            }
            return String.valueOf(value);
        }

        /**
         * Stable textual form for WAT/debug that preserves -0, NaN, and Inf.
         * Prefer hex float so {@code String.valueOf(-0.0)} cannot collapse to {@code "0.0"}.
         */
        private static String floatBitsName(double d, boolean isDouble) {
            if (isDouble) {
                long bits = Double.doubleToRawLongBits(d);
                if (bits == 0L) {
                    return "0x0p0";
                }
                if (bits == 0x8000000000000000L) {
                    return "-0x0p0";
                }
                if (Double.isNaN(d)) {
                    return "nan:0x" + Long.toHexString(bits & 0x000fffffffffffffL);
                }
                if (d == Double.POSITIVE_INFINITY) {
                    return "inf";
                }
                if (d == Double.NEGATIVE_INFINITY) {
                    return "-inf";
                }
                return Double.toHexString(d);
            }
            float f = (float) d;
            int bits = Float.floatToRawIntBits(f);
            if (bits == 0) {
                return "0x0p0";
            }
            if (bits == 0x80000000) {
                return "-0x0p0";
            }
            if (Float.isNaN(f)) {
                return "nan:0x" + Integer.toHexString(bits & 0x007fffff);
            }
            if (f == Float.POSITIVE_INFINITY) {
                return "inf";
            }
            if (f == Float.NEGATIVE_INFINITY) {
                return "-inf";
            }
            return Float.toHexString(f);
        }
    }

    record GlobalString(String symbol, String content) implements IrValue {
        @Override
        public IrType type() {
            return IrType.PTR;
        }

        @Override
        public String name() {
            return "@" + symbol;
        }
    }
}
