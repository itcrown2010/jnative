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
                return String.valueOf(((Number) value).doubleValue());
            }
            if (type == IrType.I1) {
                return Boolean.TRUE.equals(value) ? "1" : "0";
            }
            return String.valueOf(value);
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
