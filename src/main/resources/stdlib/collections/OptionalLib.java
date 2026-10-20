/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

// JNative java.util.Optional<T> subset (reference types only; no OptionalInt & co).

class Optional<T> {
    T value;
    boolean present;

    Optional(T value, boolean present) {
        this.value = value;
        this.present = present;
    }

    static <T> Optional<T> empty() {
        return new Optional<T>(null, false);
    }

    static <T> Optional<T> of(T value) {
        if (value == null) {
            throw new NullPointerException();
        }
        return new Optional<T>(value, true);
    }

    static <T> Optional<T> ofNullable(T value) {
        if (value == null) {
            return new Optional<T>(null, false);
        }
        return new Optional<T>(value, true);
    }

    boolean isPresent() {
        return present;
    }

    boolean isEmpty() {
        return !present;
    }

    T get() {
        if (!present) {
            throw new NoSuchElementException("No value present");
        }
        return value;
    }

    T orElseThrow() {
        if (!present) {
            throw new NoSuchElementException("No value present");
        }
        return value;
    }

    T orElse(T other) {
        if (present) {
            return value;
        }
        return other;
    }

    T orElseGet(Supplier<T> supplier) {
        if (present) {
            return value;
        }
        return supplier.get();
    }

    void ifPresent(Consumer<T> action) {
        if (present) {
            action.accept(value);
        }
    }

    Optional<T> filter(Predicate<T> predicate) {
        if (!present) {
            return this;
        }
        if (predicate.test(value)) {
            return this;
        }
        return new Optional<T>(null, false);
    }

    <R> Optional<R> map(Function<T, R> mapper) {
        if (!present) {
            return new Optional<R>(null, false);
        }
        R r = mapper.apply(value);
        if (r == null) {
            return new Optional<R>(null, false);
        }
        return new Optional<R>(r, true);
    }

    Optional<T> or(Supplier<Optional<T>> supplier) {
        if (present) {
            return this;
        }
        return supplier.get();
    }

    boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Optional)) {
            return false;
        }
        Optional<?> other = (Optional<?>) o;
        if (present != other.present) {
            return false;
        }
        if (!present) {
            return true;
        }
        return value.equals(other.boxedValue());
    }

    Object boxedValue() {
        Object x = value;
        return x;
    }

    int hashCode() {
        if (!present) {
            return 0;
        }
        return value.hashCode();
    }

    String toString() {
        if (!present) {
            return "Optional.empty";
        }
        return "Optional[" + value + "]";
    }
}
