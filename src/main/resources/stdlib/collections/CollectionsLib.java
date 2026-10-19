/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

// JNative Collections Framework (0.16) — library code, NOT compiler intrinsics.
// Namespace: jnative.collections / jnative.function (flattened; no package system yet).
//
// Equality: reference collections use Object.equals / Object.hashCode.
// Default Object.equals is identity; Object.hashCode is a stable identity hash
// (survives moving GC2). String / Integer / Long / Boolean override both.
// ArrayList.contains / indexOf: identity first, then equals (wrappers / String).
// Primitive specialization lowers T.equals to value ==.
// Null keys unsupported in HashMap; null values allowed.
// Mutable keys: mutating fields that affect hashCode/equals after put is undefined.

/** Resource that can be used in try-with-resources.
 * @author 张晓达
 */
interface AutoCloseable {
    void close();
}

/**
 * Debug-oriented open-resource counter (Round 2).
 * Not a full OS handle registry — use for TWR / leak smoke tests.
 */
class ResourceTracker {
    static int opens;
    static int closes;
    static int filesOpen;
    static int socketsOpen;
    static int tlsOpen;
    static int serversOpen;
    static int buffersOpen;

    static void trackOpen(String kind) {
        opens = opens + 1;
        if (kind == null) {
            return;
        }
        if (kind.equals("file")) {
            filesOpen = filesOpen + 1;
        } else if (kind.equals("socket")) {
            socketsOpen = socketsOpen + 1;
        } else if (kind.equals("tls")) {
            tlsOpen = tlsOpen + 1;
        } else if (kind.equals("server")) {
            serversOpen = serversOpen + 1;
        } else if (kind.equals("buffer")) {
            buffersOpen = buffersOpen + 1;
        }
    }

    static void trackClose(String kind) {
        closes = closes + 1;
        if (kind == null) {
            return;
        }
        if (kind.equals("file")) {
            if (filesOpen > 0) {
                filesOpen = filesOpen - 1;
            }
        } else if (kind.equals("socket")) {
            if (socketsOpen > 0) {
                socketsOpen = socketsOpen - 1;
            }
        } else if (kind.equals("tls")) {
            if (tlsOpen > 0) {
                tlsOpen = tlsOpen - 1;
            }
        } else if (kind.equals("server")) {
            if (serversOpen > 0) {
                serversOpen = serversOpen - 1;
            }
        } else if (kind.equals("buffer")) {
            if (buffersOpen > 0) {
                buffersOpen = buffersOpen - 1;
            }
        }
    }

    static int openCount() {
        int n = opens - closes;
        if (n < 0) {
            return 0;
        }
        return n;
    }

    static int files() {
        return filesOpen;
    }

    static int sockets() {
        return socketsOpen;
    }

    static int tls() {
        return tlsOpen;
    }

    static int servers() {
        return serversOpen;
    }

    static int buffers() {
        return buffersOpen;
    }

    static void reset() {
        opens = 0;
        closes = 0;
        filesOpen = 0;
        socketsOpen = 0;
        tlsOpen = 0;
        serversOpen = 0;
        buffersOpen = 0;
    }
}

interface Iterable<T> {
    Iterator<T> iterator();
}

interface Iterator<T> {
    boolean hasNext();
    T next();
}

interface Collection<T> extends Iterable<T> {
    int size();
    boolean isEmpty();
    boolean contains(T value);
    void clear();
    boolean addAll(Collection<T> other);
    boolean containsAll(Collection<T> other);
    boolean removeIf(Predicate<T> filter);
    boolean removeAll(Collection<T> other);
    boolean retainAll(Collection<T> other);
    void forEach(Consumer<T> action);
}

interface List<T> extends Collection<T> {
    T get(int index);
    T set(int index, T value);
    boolean add(T value);
    void addAt(int index, T value);
    /** Remove by index (not overloaded with value remove — avoids Integer ambiguity). */
    T removeAt(int index);
    /** Remove first equal element; returns whether an element was removed. */
    boolean removeValue(T value);
    int indexOf(T value);
    int lastIndexOf(T value);
    void sort(Comparator<T> c);
    List<T> subList(int fromIndex, int toIndex);
}

interface Set<T> extends Collection<T> {
    boolean add(T value);
    boolean remove(T value);
}

interface Map<K, V> {
    int size();
    boolean isEmpty();
    V get(K key);
    V put(K key, V value);
    V remove(K key);
    boolean containsKey(K key);
    void clear();
    boolean containsValue(V value);
    V getOrDefault(K key, V defaultValue);
    V putIfAbsent(K key, V value);
    void putAll(Map<K, V> other);
    V replace(K key, V value);
    void forEach(BiConsumer<K, V> action);
    V merge(K key, V value, BiFunction<V, V, V> fn);
    V computeIfAbsent(K key, Function<K, V> fn);
    V computeIfPresent(K key, BiFunction<K, V, V> fn);
    Set<K> keySet();
    Collection<V> values();
    Set<Entry<K, V>> entrySet();
}

interface Entry<K, V> {
    K getKey();
    V getValue();
    V setValue(V value);
}

interface BiConsumer<T, U> {
    void accept(T a, U b);
}

interface BiFunction<T, U, R> {
    R apply(T a, U b);
}

/**
 * Core Collections 1.0 Queue surface (does not extend Collection yet —
 * primitive specialization + deep interface hierarchies are fragile).
 */
interface Queue<T> {
    int size();
    boolean isEmpty();
    boolean offer(T value);
    T poll();
    T peek();
}

/** Core Collections 1.0 Deque surface. */
interface Deque<T> {
    int size();
    boolean isEmpty();
    void addFirst(T value);
    void addLast(T value);
    T removeFirst();
    T removeLast();
    T peekFirst();
    T peekLast();
    boolean offer(T value);
    T poll();
    T peek();
}

interface Comparator<T> {
    int compare(T a, T b);
}

interface Comparable<T> {
    int compareTo(T other);
}

interface Consumer<T> {
    void accept(T value);
}

interface Function<T, R> {
    R apply(T value);
}

interface Predicate<T> {
    boolean test(T value);
}

interface Supplier<T> {
    T get();
}

/**
 * Primitive-specialized SAMs (avoid boxing when targeted).
 * Note: int→int already exists as builtins {@code IntFunction}/{@code IntUnaryFunction}
 * (do not redefine here — parallel incompatible SAMs are forbidden).
 */
interface IntConsumer {
    void accept(int value);
}

interface IntPredicate {
    boolean test(int value);
}

interface IntSupplier {
    int getAsInt();
}

class ArrayListIterator<T> implements Iterator<T> {
    ArrayList<T> list;
    int index;
    int expectedModCount;

    ArrayListIterator(ArrayList<T> list) {
        this.list = list;
        this.index = 0;
        this.expectedModCount = list.modCount;
    }

    public boolean hasNext() {
        return this.index < this.list.size;
    }

    public T next() {
        if (this.expectedModCount != this.list.modCount) {
            throw new ConcurrentModificationException("ArrayList modified during iteration");
        }
        if (this.index >= this.list.size) {
            throw new NoSuchElementException("ArrayListIterator");
        }
        T value = this.list.storage[this.index];
        this.index = this.index + 1;
        return value;
    }
}

class ArrayList<T> implements List<T> {
    T[] storage;
    int size;
    int modCount;

    ArrayList() {
        this.storage = null;
        this.size = 0;
        this.modCount = 0;
    }

    ArrayList(int initialCapacity) {
        this.modCount = 0;
        this.size = 0;
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("negative initialCapacity");
        }
        if (initialCapacity == 0) {
            this.storage = null;
        } else {
            this.storage = new T[initialCapacity];
        }
    }

    static int calculateNewCapacity(int oldCapacity) {
        if (oldCapacity <= 0) {
            return 10;
        }
        int grown = oldCapacity + (oldCapacity / 2);
        if (grown < oldCapacity) {
            // int overflow — refuse rather than leave a corrupted list
            throw new IllegalStateException("ArrayList capacity overflow");
        }
        return grown;
    }

    void ensureCapacity(int minCapacity) {
        int cap = 0;
        if (this.storage != null) {
            cap = this.storage.length;
        }
        if (minCapacity <= cap) {
            return;
        }
        if (minCapacity < 0) {
            throw new IllegalStateException("ArrayList capacity overflow");
        }
        int newCap = calculateNewCapacity(cap);
        if (newCap < minCapacity) {
            newCap = minCapacity;
        }
        T[] old = this.storage;
        T[] neu = new T[newCap];
        int i = 0;
        while (i < this.size) {
            neu[i] = old[i];
            i = i + 1;
        }
        this.storage = neu;
    }

    void rangeCheck(int index) {
        if (index < 0 || index >= this.size) {
            throw new IndexOutOfBoundsException("ArrayList index");
        }
    }

    public int size() {
        return this.size;
    }

    public boolean isEmpty() {
        return this.size == 0;
    }

    public T get(int index) {
        rangeCheck(index);
        return this.storage[index];
    }

    public T set(int index, T value) {
        rangeCheck(index);
        T old = this.storage[index];
        this.storage[index] = value;
        return old;
    }

    public boolean add(T value) {
        ensureCapacity(this.size + 1);
        this.storage[this.size] = value;
        this.size = this.size + 1;
        this.modCount = this.modCount + 1;
        return true;
    }

    public void addAt(int index, T value) {
        if (index < 0 || index > this.size) {
            throw new IndexOutOfBoundsException("ArrayList insert index");
        }
        ensureCapacity(this.size + 1);
        int i = this.size;
        while (i > index) {
            this.storage[i] = this.storage[i - 1];
            i = i - 1;
        }
        this.storage[index] = value;
        this.size = this.size + 1;
        this.modCount = this.modCount + 1;
    }

    public T removeAt(int index) {
        rangeCheck(index);
        T old = this.storage[index];
        int i = index;
        while (i + 1 < this.size) {
            this.storage[i] = this.storage[i + 1];
            i = i + 1;
        }
        this.size = this.size - 1;
        this.storage[this.size] = null;
        this.modCount = this.modCount + 1;
        return old;
    }

    public int indexOf(T value) {
        int i = 0;
        while (i < this.size) {
            T cur = this.storage[i];
            if (cur == value) {
                return i;
            }
            // equals for wrappers/String; primitive specialization lowers equals → ==
            if (valueEquals(value, cur)) {
                return i;
            }
            i = i + 1;
        }
        return -1;
    }

    /**
     * Null-safe equals for reference elements. For primitive specialization,
     * {@code a.equals(b)} lowers to {@code a == b} (see IrGenerator).
     */
    boolean valueEquals(T a, T b) {
        if (a == b) {
            return true;
        }
        if (a == null) {
            return false;
        }
        return a.equals(b);
    }

    public boolean contains(T value) {
        return indexOf(value) >= 0;
    }

    public boolean removeValue(T value) {
        int idx = indexOf(value);
        if (idx < 0) {
            return false;
        }
        removeAt(idx);
        return true;
    }

    public void clear() {
        int i = 0;
        while (i < this.size) {
            this.storage[i] = null;
            i = i + 1;
        }
        this.size = 0;
        this.modCount = this.modCount + 1;
    }

    public Iterator<T> iterator() {
        return new ArrayListIterator<T>(this);
    }

    ArrayList(Collection<T> other) {
        this.storage = null;
        this.size = 0;
        this.modCount = 0;
        addAll(other);
    }

    public boolean addAll(Collection<T> other) {
        int extra = other.size();
        if (extra == 0) {
            return false;
        }
        Object otherObj = other;
        Object selfObj = this;
        ensureCapacity(this.size + extra);
        if (otherObj == selfObj) {
            int i = 0;
            while (i < extra) {
                this.storage[this.size + i] = this.storage[i];
                i = i + 1;
            }
            this.size = this.size + extra;
        } else {
            Iterator<T> it = other.iterator();
            while (it.hasNext()) {
                this.storage[this.size] = it.next();
                this.size = this.size + 1;
            }
        }
        this.modCount = this.modCount + 1;
        return true;
    }

    public boolean containsAll(Collection<T> other) {
        Iterator<T> it = other.iterator();
        while (it.hasNext()) {
            if (!contains(it.next())) {
                return false;
            }
        }
        return true;
    }

    public boolean removeAll(Collection<T> other) {
        return removeIf(x -> other.contains(x));
    }

    public boolean retainAll(Collection<T> other) {
        return removeIf(x -> !other.contains(x));
    }

    public int lastIndexOf(T value) {
        int i = this.size - 1;
        while (i >= 0) {
            if (valueEquals(value, this.storage[i])) {
                return i;
            }
            i = i - 1;
        }
        return -1;
    }

    public boolean removeIf(Predicate<T> filter) {
        if (filter == null) {
            throw new NullPointerException("filter");
        }
        int keep = 0;
        int i = 0;
        while (i < this.size) {
            T v = this.storage[i];
            if (!filter.test(v)) {
                this.storage[keep] = v;
                keep = keep + 1;
            }
            i = i + 1;
        }
        boolean changed = keep != this.size;
        int j = keep;
        while (j < this.size) {
            this.storage[j] = null;
            j = j + 1;
        }
        this.size = keep;
        if (changed) {
            this.modCount = this.modCount + 1;
        }
        return changed;
    }

    int compareElems(Comparator<T> c, T a, T b) {
        if (c == null) {
            return SortSupport.natural(a, b);
        }
        return c.compare(a, b);
    }

    /** Stable merge sort; {@code null} comparator = natural ordering. */
    public void sort(Comparator<T> c) {
        if (this.size > 1) {
            T[] src = this.storage;
            int[] order = SortSupport.sortedOrder(this.size, (i, j) -> compareElems(c, src[i], src[j]));
            T[] out = new T[src.length];
            int k = 0;
            while (k < this.size) {
                out[k] = src[order[k]];
                k = k + 1;
            }
            this.storage = out;
        }
        this.modCount = this.modCount + 1;
    }
    /** Copy of the range (Java returns a view; mutations are not reflected). */
    public List<T> subList(int fromIndex, int toIndex) {
        if (fromIndex < 0 || toIndex > this.size || fromIndex > toIndex) {
            throw new IndexOutOfBoundsException("subList");
        }
        ArrayList<T> out = new ArrayList<T>();
        int i = fromIndex;
        while (i < toIndex) {
            out.add(this.storage[i]);
            i = i + 1;
        }
        return out;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        int i = 0;
        while (i < this.size) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(String.valueOf(this.storage[i]));
            i = i + 1;
        }
        sb.append("]");
        return sb.toString();
    }

    public boolean equals(Object o) {
        Object selfObj = this;
        if (o == selfObj) {
            return true;
        }
        if (!(o instanceof ArrayList)) {
            return false;
        }
        ArrayList<T> other = (ArrayList<T>) o;
        if (other.size != this.size) {
            return false;
        }
        int i = 0;
        while (i < this.size) {
            if (!valueEquals(this.storage[i], other.storage[i])) {
                return false;
            }
            i = i + 1;
        }
        return true;
    }

    public int hashCode() {
        int h = 1;
        int i = 0;
        while (i < this.size) {
            Object x = this.storage[i];
            int eh = 0;
            if (x != null) {
                eh = x.hashCode();
            }
            h = 31 * h + eh;
            i = i + 1;
        }
        return h;
    }

    /**
     * Invoke {@code action} for each element in index order.
     * Structural modification of this list during forEach → ConcurrentModificationException.
     * Callback exceptions propagate to the caller. {@code action} null → NPE.
     */
    public void forEach(Consumer<T> action) {
        if (action == null) {
            throw new NullPointerException("action");
        }
        int expected = this.modCount;
        int i = 0;
        while (i < this.size) {
            if (expected != this.modCount) {
                throw new ConcurrentModificationException("ArrayList modified during forEach");
            }
            action.accept(this.storage[i]);
            i = i + 1;
        }
    }

    /**
     * New list of elements for which {@code predicate.test} is true (index order preserved).
     * Result is independent of this list. Structural modification during filter → CME.
     */
    public ArrayList<T> filter(Predicate<T> predicate) {
        if (predicate == null) {
            throw new NullPointerException("predicate");
        }
        ArrayList<T> out = new ArrayList<T>();
        int expected = this.modCount;
        int i = 0;
        while (i < this.size) {
            if (expected != this.modCount) {
                throw new ConcurrentModificationException("ArrayList modified during filter");
            }
            T v = this.storage[i];
            if (predicate.test(v)) {
                out.add(v);
            }
            i = i + 1;
        }
        return out;
    }

    /**
     * Map each element through {@code mapper} into a new {@code ArrayList<Object>}
     * (same length, index order). Result is independent. Structural modification → CME.
     * (Method type-parameter map&lt;R&gt; deferred; use Object results + cast.)
     */
    public ArrayList<Object> mapValues(Function<T, Object> mapper) {
        if (mapper == null) {
            throw new NullPointerException("mapper");
        }
        ArrayList<Object> out = new ArrayList<Object>();
        int expected = this.modCount;
        int i = 0;
        while (i < this.size) {
            if (expected != this.modCount) {
                throw new ConcurrentModificationException("ArrayList modified during mapValues");
            }
            Object mapped = mapper.apply(this.storage[i]);
            out.add(mapped);
            i = i + 1;
        }
        return out;
    }
}

/**
 * Chained hash table with the same bucketing, resize policy and insertion order as
 * java.util.HashMap (so iteration order matches Java for the same keys/hash codes).
 * Null keys and null values are allowed. Bins are never treeified.
 */
class HashMap<K, V> implements Map<K, V> {
    HashMapEntry<K, V>[] table;
    int size;
    int modCount;
    int threshold;

    HashMap() {
        this.table = null;
        this.size = 0;
        this.modCount = 0;
        this.threshold = 0;
    }

    HashMap(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("negative initialCapacity");
        }
        this.table = null;
        this.size = 0;
        this.modCount = 0;
        this.threshold = tableSizeFor(initialCapacity);
    }

    HashMap(Map<K, V> other) {
        this.table = null;
        this.size = 0;
        this.modCount = 0;
        this.threshold = 0;
        putAll(other);
    }

    static int tableSizeFor(int cap) {
        int n = 1;
        while (n < cap) {
            n = n + n;
        }
        return n;
    }

    int hashOf(K key) {
        if (key == null) {
            return 0;
        }
        int h = key.hashCode();
        return h ^ (h >>> 16);
    }

    boolean keysEqual(K a, K b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.equals(b);
    }

    void resize() {
        HashMapEntry<K, V>[] old = this.table;
        int oldCap = 0;
        if (old != null) {
            oldCap = old.length;
        }
        int newCap = 16;
        if (oldCap > 0) {
            newCap = oldCap + oldCap;
        } else if (this.threshold > 0) {
            newCap = this.threshold;
        }
        this.threshold = (newCap * 3) / 4;
        HashMapEntry<K, V>[] neu = new HashMapEntry<K, V>[newCap];
        HashMapEntry<K, V>[] tails = new HashMapEntry<K, V>[newCap];
        int i = 0;
        while (i < oldCap) {
            HashMapEntry<K, V> e = old[i];
            while (e != null) {
                HashMapEntry<K, V> nxt = e.next;
                e.next = null;
                int idx = e.hash & (newCap - 1);
                if (tails[idx] == null) {
                    neu[idx] = e;
                } else {
                    tails[idx].next = e;
                }
                tails[idx] = e;
                e = nxt;
            }
            i = i + 1;
        }
        this.table = neu;
    }

    HashMapEntry<K, V> findNode(K key) {
        if (this.table == null) {
            return null;
        }
        int h = hashOf(key);
        HashMapEntry<K, V> e = this.table[h & (this.table.length - 1)];
        while (e != null) {
            if (e.hash == h && keysEqual(e.key, key)) {
                return e;
            }
            e = e.next;
        }
        return null;
    }

    public int size() {
        return this.size;
    }

    public boolean isEmpty() {
        return this.size == 0;
    }

    public V get(K key) {
        HashMapEntry<K, V> e = findNode(key);
        if (e == null) {
            return null;
        }
        return e.value;
    }

    public boolean containsKey(K key) {
        return findNode(key) != null;
    }

    public V put(K key, V value) {
        int h = hashOf(key);
        if (this.table == null) {
            resize();
        }
        int idx = h & (this.table.length - 1);
        HashMapEntry<K, V> e = this.table[idx];
        HashMapEntry<K, V> last = null;
        while (e != null) {
            if (e.hash == h && keysEqual(e.key, key)) {
                V old = e.value;
                e.value = value;
                return old;
            }
            last = e;
            e = e.next;
        }
        HashMapEntry<K, V> n = new HashMapEntry<K, V>(h, key, value);
        if (last == null) {
            this.table[idx] = n;
        } else {
            last.next = n;
        }
        this.modCount = this.modCount + 1;
        this.size = this.size + 1;
        if (this.size > this.threshold) {
            resize();
        }
        return null;
    }

    public V remove(K key) {
        if (this.table == null) {
            return null;
        }
        int h = hashOf(key);
        int idx = h & (this.table.length - 1);
        HashMapEntry<K, V> e = this.table[idx];
        HashMapEntry<K, V> prev = null;
        while (e != null) {
            if (e.hash == h && keysEqual(e.key, key)) {
                if (prev == null) {
                    this.table[idx] = e.next;
                } else {
                    prev.next = e.next;
                }
                this.modCount = this.modCount + 1;
                this.size = this.size - 1;
                return e.value;
            }
            prev = e;
            e = e.next;
        }
        return null;
    }

    public void clear() {
        if (this.table != null) {
            int i = 0;
            while (i < this.table.length) {
                this.table[i] = null;
                i = i + 1;
            }
        }
        this.size = 0;
        this.modCount = this.modCount + 1;
    }

    public boolean containsValue(V value) {
        HashMapCursor<K, V> c = new HashMapCursor<K, V>(this);
        while (c.hasNext()) {
            V v = c.nextEntry().value;
            if (v == value) {
                return true;
            }
            if (v != null && v.equals(value)) {
                return true;
            }
        }
        return false;
    }

    public V getOrDefault(K key, V defaultValue) {
        HashMapEntry<K, V> e = findNode(key);
        if (e == null) {
            return defaultValue;
        }
        return e.value;
    }

    public V putIfAbsent(K key, V value) {
        HashMapEntry<K, V> e = findNode(key);
        if (e == null) {
            put(key, value);
            return null;
        }
        return e.value;
    }

    public void putAll(Map<K, V> other) {
        Iterator<Entry<K, V>> it = other.entrySet().iterator();
        while (it.hasNext()) {
            Entry<K, V> e = it.next();
            put(e.getKey(), e.getValue());
        }
    }

    public V replace(K key, V value) {
        HashMapEntry<K, V> e = findNode(key);
        if (e == null) {
            return null;
        }
        V old = e.value;
        e.value = value;
        return old;
    }

    public void forEach(BiConsumer<K, V> action) {
        int expected = this.modCount;
        HashMapCursor<K, V> c = new HashMapCursor<K, V>(this);
        while (c.hasNext()) {
            HashMapEntry<K, V> e = c.nextEntry();
            action.accept(e.key, e.value);
            if (expected != this.modCount) {
                throw new ConcurrentModificationException("HashMap modified during forEach");
            }
        }
    }

    public V merge(K key, V value, BiFunction<V, V, V> fn) {
        HashMapEntry<K, V> e = findNode(key);
        if (e == null) {
            put(key, value);
            return value;
        }
        V nv = fn.apply(e.value, value);
        e.value = nv;
        return nv;
    }

    public V computeIfAbsent(K key, Function<K, V> fn) {
        HashMapEntry<K, V> e = findNode(key);
        if (e != null) {
            return e.value;
        }
        V nv = fn.apply(key);
        put(key, nv);
        return nv;
    }

    public V computeIfPresent(K key, BiFunction<K, V, V> fn) {
        HashMapEntry<K, V> e = findNode(key);
        if (e == null) {
            return null;
        }
        V nv = fn.apply(key, e.value);
        e.value = nv;
        return nv;
    }

    public Set<K> keySet() {
        return new HashMapKeySet<K, V>(this);
    }

    public Collection<V> values() {
        return new HashMapValues<K, V>(this);
    }

    public Set<Entry<K, V>> entrySet() {
        return new HashMapEntrySet<K, V>(this);
    }

    /** Snapshot of keys in iteration order. */
    public K[] keysArray() {
        K[] out = new K[this.size];
        int n = 0;
        HashMapCursor<K, V> c = new HashMapCursor<K, V>(this);
        while (c.hasNext()) {
            out[n] = c.nextEntry().key;
            n = n + 1;
        }
        return out;
    }

    public HashMapEntryIterator<K, V> entryIterator() {
        return new HashMapEntryIterator<K, V>(this);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        HashMapCursor<K, V> c = new HashMapCursor<K, V>(this);
        boolean first = true;
        while (c.hasNext()) {
            HashMapEntry<K, V> e = c.nextEntry();
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append(String.valueOf(e.key));
            sb.append("=");
            sb.append(String.valueOf(e.value));
        }
        sb.append("}");
        return sb.toString();
    }

    public boolean equals(Object o) {
        Object selfObj = this;
        if (o == selfObj) {
            return true;
        }
        if (!(o instanceof HashMap)) {
            return false;
        }
        HashMap<K, V> other = (HashMap<K, V>) o;
        if (other.size != this.size) {
            return false;
        }
        HashMapCursor<K, V> c = new HashMapCursor<K, V>(this);
        while (c.hasNext()) {
            HashMapEntry<K, V> e = c.nextEntry();
            HashMapEntry<K, V> oe = other.findNode(e.key);
            if (oe == null) {
                return false;
            }
            V a = e.value;
            V b = oe.value;
            if (a != b) {
                if (a == null || b == null) {
                    return false;
                }
                if (!a.equals(b)) {
                    return false;
                }
            }
        }
        return true;
    }

    public int hashCode() {
        int h = 0;
        HashMapCursor<K, V> c = new HashMapCursor<K, V>(this);
        while (c.hasNext()) {
            h = h + c.nextEntry().hashCode();
        }
        return h;
    }
}

class HashMapEntry<K, V> implements Entry<K, V> {
    int hash;
    K key;
    V value;
    HashMapEntry<K, V> next;

    HashMapEntry(int hash, K key, V value) {
        this.hash = hash;
        this.key = key;
        this.value = value;
        this.next = null;
    }

    public K getKey() {
        return this.key;
    }

    public V getValue() {
        return this.value;
    }

    public V setValue(V value) {
        V old = this.value;
        this.value = value;
        return old;
    }

    public String toString() {
        return String.valueOf(this.key) + "=" + String.valueOf(this.value);
    }

    public boolean equals(Object o) {
        if (!(o instanceof HashMapEntry)) {
            return false;
        }
        HashMapEntry<K, V> other = (HashMapEntry<K, V>) o;
        Object k1 = this.key;
        Object k2 = other.key;
        Object v1 = this.value;
        Object v2 = other.value;
        if (k1 != k2 && (k1 == null || !k1.equals(k2))) {
            return false;
        }
        if (v1 != v2 && (v1 == null || !v1.equals(v2))) {
            return false;
        }
        return true;
    }

    public int hashCode() {
        Object k = this.key;
        Object v = this.value;
        int kh = 0;
        int vh = 0;
        if (k != null) {
            kh = k.hashCode();
        }
        if (v != null) {
            vh = v.hashCode();
        }
        return kh ^ vh;
    }
}

/** Shared bucket-walking cursor (fail-fast on structural modification). */
class HashMapCursor<K, V> {
    HashMap<K, V> map;
    HashMapEntry<K, V> upcoming;
    int bucket;
    int expectedModCount;

    HashMapCursor(HashMap<K, V> map) {
        this.map = map;
        this.expectedModCount = map.modCount;
        this.bucket = 0;
        this.upcoming = null;
        advanceBucket();
    }

    void advanceBucket() {
        HashMapEntry<K, V>[] t = this.map.table;
        if (t == null) {
            return;
        }
        while (this.upcoming == null && this.bucket < t.length) {
            this.upcoming = t[this.bucket];
            this.bucket = this.bucket + 1;
        }
    }

    boolean hasNext() {
        return this.upcoming != null;
    }

    HashMapEntry<K, V> nextEntry() {
        if (this.expectedModCount != this.map.modCount) {
            throw new ConcurrentModificationException("HashMap modified during iteration");
        }
        HashMapEntry<K, V> e = this.upcoming;
        if (e == null) {
            throw new NoSuchElementException("HashMap iterator");
        }
        this.upcoming = e.next;
        if (this.upcoming == null) {
            advanceBucket();
        }
        return e;
    }
}

class HashMapEntryIterator<K, V> implements Iterator<HashMapEntry<K, V>> {
    HashMapCursor<K, V> cursor;

    HashMapEntryIterator(HashMap<K, V> map) {
        this.cursor = new HashMapCursor<K, V>(map);
    }

    public boolean hasNext() {
        return this.cursor.hasNext();
    }

    public HashMapEntry<K, V> next() {
        return this.cursor.nextEntry();
    }
}

class HashMapKeyIterator<K, V> implements Iterator<K> {
    HashMapCursor<K, V> cursor;

    HashMapKeyIterator(HashMap<K, V> map) {
        this.cursor = new HashMapCursor<K, V>(map);
    }

    public boolean hasNext() {
        return this.cursor.hasNext();
    }

    public K next() {
        return this.cursor.nextEntry().key;
    }
}

class HashMapValueIterator<K, V> implements Iterator<V> {
    HashMapCursor<K, V> cursor;

    HashMapValueIterator(HashMap<K, V> map) {
        this.cursor = new HashMapCursor<K, V>(map);
    }

    public boolean hasNext() {
        return this.cursor.hasNext();
    }

    public V next() {
        return this.cursor.nextEntry().value;
    }
}

class HashMapEntryViewIterator<K, V> implements Iterator<Entry<K, V>> {
    HashMapCursor<K, V> cursor;

    HashMapEntryViewIterator(HashMap<K, V> map) {
        this.cursor = new HashMapCursor<K, V>(map);
    }

    public boolean hasNext() {
        return this.cursor.hasNext();
    }

    public Entry<K, V> next() {
        return this.cursor.nextEntry();
    }
}

/** Live key view (remove-capable, add unsupported). */
class HashMapKeySet<K, V> implements Set<K> {
    HashMap<K, V> map;

    HashMapKeySet(HashMap<K, V> map) {
        this.map = map;
    }

    public int size() {
        return this.map.size;
    }

    public boolean isEmpty() {
        return this.map.size == 0;
    }

    public boolean contains(K value) {
        return this.map.containsKey(value);
    }

    public void clear() {
        this.map.clear();
    }

    public boolean add(K value) {
        throw new UnsupportedOperationException("add");
    }

    public boolean remove(K value) {
        if (!this.map.containsKey(value)) {
            return false;
        }
        this.map.remove(value);
        return true;
    }

    public boolean addAll(Collection<K> other) {
        throw new UnsupportedOperationException("addAll");
    }

    public boolean containsAll(Collection<K> other) {
        Iterator<K> it = other.iterator();
        while (it.hasNext()) {
            if (!contains(it.next())) {
                return false;
            }
        }
        return true;
    }

    public boolean removeIf(Predicate<K> filter) {
        ArrayList<K> doomed = new ArrayList<K>();
        HashMapCursor<K, V> c = new HashMapCursor<K, V>(this.map);
        while (c.hasNext()) {
            K k = c.nextEntry().key;
            if (filter.test(k)) {
                doomed.add(k);
            }
        }
        int i = 0;
        while (i < doomed.size()) {
            this.map.remove(doomed.get(i));
            i = i + 1;
        }
        return doomed.size() > 0;
    }

    public boolean removeAll(Collection<K> other) {
        return removeIf(x -> other.contains(x));
    }

    public boolean retainAll(Collection<K> other) {
        return removeIf(x -> !other.contains(x));
    }

    public void forEach(Consumer<K> action) {
        Iterator<K> it = iterator();
        while (it.hasNext()) {
            action.accept(it.next());
        }
    }

    public Iterator<K> iterator() {
        return new HashMapKeyIterator<K, V>(this.map);
    }

    public String toString() {
        return CollectionText.join(iterator());
    }
}

class HashMapValues<K, V> implements Collection<V> {
    HashMap<K, V> map;

    HashMapValues(HashMap<K, V> map) {
        this.map = map;
    }

    public int size() {
        return this.map.size;
    }

    public boolean isEmpty() {
        return this.map.size == 0;
    }

    public boolean contains(V value) {
        return this.map.containsValue(value);
    }

    public void clear() {
        this.map.clear();
    }

    public boolean addAll(Collection<V> other) {
        throw new UnsupportedOperationException("addAll");
    }

    public boolean containsAll(Collection<V> other) {
        Iterator<V> it = other.iterator();
        while (it.hasNext()) {
            if (!contains(it.next())) {
                return false;
            }
        }
        return true;
    }

    public boolean removeIf(Predicate<V> filter) {
        ArrayList<K> doomed = new ArrayList<K>();
        HashMapCursor<K, V> c = new HashMapCursor<K, V>(this.map);
        while (c.hasNext()) {
            HashMapEntry<K, V> e = c.nextEntry();
            if (filter.test(e.value)) {
                doomed.add(e.key);
            }
        }
        int i = 0;
        while (i < doomed.size()) {
            this.map.remove(doomed.get(i));
            i = i + 1;
        }
        return doomed.size() > 0;
    }

    public boolean removeAll(Collection<V> other) {
        return removeIf(x -> other.contains(x));
    }

    public boolean retainAll(Collection<V> other) {
        return removeIf(x -> !other.contains(x));
    }

    public void forEach(Consumer<V> action) {
        Iterator<V> it = iterator();
        while (it.hasNext()) {
            action.accept(it.next());
        }
    }

    public Iterator<V> iterator() {
        return new HashMapValueIterator<K, V>(this.map);
    }

    public String toString() {
        return CollectionText.join(iterator());
    }
}

class HashMapEntrySet<K, V> implements Set<Entry<K, V>> {
    HashMap<K, V> map;

    HashMapEntrySet(HashMap<K, V> map) {
        this.map = map;
    }

    public int size() {
        return this.map.size;
    }

    public boolean isEmpty() {
        return this.map.size == 0;
    }

    public boolean contains(Entry<K, V> value) {
        HashMapEntry<K, V> e = this.map.findNode(value.getKey());
        if (e == null) {
            return false;
        }
        Object a = e.value;
        Object b = value.getValue();
        if (a == b) {
            return true;
        }
        return a != null && a.equals(b);
    }

    public void clear() {
        this.map.clear();
    }

    public boolean add(Entry<K, V> value) {
        throw new UnsupportedOperationException("add");
    }

    public boolean remove(Entry<K, V> value) {
        if (!contains(value)) {
            return false;
        }
        this.map.remove(value.getKey());
        return true;
    }

    public boolean addAll(Collection<Entry<K, V>> other) {
        throw new UnsupportedOperationException("addAll");
    }

    public boolean containsAll(Collection<Entry<K, V>> other) {
        Iterator<Entry<K, V>> it = other.iterator();
        while (it.hasNext()) {
            if (!contains(it.next())) {
                return false;
            }
        }
        return true;
    }

    public boolean removeIf(Predicate<Entry<K, V>> filter) {
        ArrayList<K> doomed = new ArrayList<K>();
        HashMapCursor<K, V> c = new HashMapCursor<K, V>(this.map);
        while (c.hasNext()) {
            HashMapEntry<K, V> e = c.nextEntry();
            if (filter.test(e)) {
                doomed.add(e.key);
            }
        }
        int i = 0;
        while (i < doomed.size()) {
            this.map.remove(doomed.get(i));
            i = i + 1;
        }
        return doomed.size() > 0;
    }

    public boolean removeAll(Collection<Entry<K, V>> other) {
        return removeIf(x -> other.contains(x));
    }

    public boolean retainAll(Collection<Entry<K, V>> other) {
        return removeIf(x -> !other.contains(x));
    }

    public void forEach(Consumer<Entry<K, V>> action) {
        Iterator<Entry<K, V>> it = iterator();
        while (it.hasNext()) {
            action.accept(it.next());
        }
    }

    public Iterator<Entry<K, V>> iterator() {
        return new HashMapEntryViewIterator<K, V>(this.map);
    }

    public String toString() {
        return CollectionText.join(iterator());
    }
}

class CollectionText {
    /** Java AbstractCollection.toString format: [a, b, c]. */
    static <T> String join(Iterator<T> it) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        boolean first = true;
        while (it.hasNext()) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append(String.valueOf(it.next()));
        }
        sb.append("]");
        return sb.toString();
    }
}

class PresentMarker {
}

class ArrayIterator<T> implements Iterator<T> {
    T[] data;
    int index;
    int end;

    ArrayIterator(T[] data) {
        this.data = data;
        this.index = 0;
        if (data == null) {
            this.end = 0;
        } else {
            this.end = data.length;
        }
    }

    public boolean hasNext() {
        return this.index < this.end;
    }

    public T next() {
        if (this.index >= this.end) {
            throw new NoSuchElementException("ArrayIterator");
        }
        T value = this.data[this.index];
        this.index = this.index + 1;
        return value;
    }
}

class HashSet<T> implements Set<T> {
    HashMap<T, PresentMarker> map;
    PresentMarker present;

    HashSet() {
        this.map = new HashMap<T, PresentMarker>();
        this.present = new PresentMarker();
    }

    HashSet(int initialCapacity) {
        this.map = new HashMap<T, PresentMarker>(initialCapacity);
        this.present = new PresentMarker();
    }

    HashSet(Collection<T> other) {
        int cap = (other.size() * 4) / 3 + 1;
        if (cap < 16) {
            cap = 16;
        }
        this.map = new HashMap<T, PresentMarker>(cap);
        this.present = new PresentMarker();
        addAll(other);
    }

    public int size() {
        return this.map.size();
    }

    public boolean isEmpty() {
        return this.map.isEmpty();
    }

    public boolean contains(T value) {
        return this.map.containsKey(value);
    }

    public boolean add(T value) {
        return this.map.put(value, this.present) == null;
    }

    public boolean remove(T value) {
        return this.map.remove(value) != null;
    }

    public void clear() {
        this.map.clear();
    }

    public boolean addAll(Collection<T> other) {
        boolean changed = false;
        Iterator<T> it = other.iterator();
        while (it.hasNext()) {
            if (add(it.next())) {
                changed = true;
            }
        }
        return changed;
    }

    public boolean containsAll(Collection<T> other) {
        Iterator<T> it = other.iterator();
        while (it.hasNext()) {
            if (!contains(it.next())) {
                return false;
            }
        }
        return true;
    }

    public boolean removeIf(Predicate<T> filter) {
        ArrayList<T> doomed = new ArrayList<T>();
        Iterator<T> it = iterator();
        while (it.hasNext()) {
            T v = it.next();
            if (filter.test(v)) {
                doomed.add(v);
            }
        }
        int i = 0;
        while (i < doomed.size()) {
            this.map.remove(doomed.get(i));
            i = i + 1;
        }
        return doomed.size() > 0;
    }

    public boolean removeAll(Collection<T> other) {
        return removeIf(x -> other.contains(x));
    }

    public boolean retainAll(Collection<T> other) {
        return removeIf(x -> !other.contains(x));
    }

    public void forEach(Consumer<T> action) {
        Iterator<T> it = iterator();
        while (it.hasNext()) {
            action.accept(it.next());
        }
    }

    public Iterator<T> iterator() {
        return new HashMapKeyIterator<T, PresentMarker>(this.map);
    }

    public String toString() {
        return CollectionText.join(iterator());
    }

    public boolean equals(Object o) {
        Object selfObj = this;
        if (o == selfObj) {
            return true;
        }
        if (!(o instanceof HashSet)) {
            return false;
        }
        HashSet<T> other = (HashSet<T>) o;
        if (other.size() != size()) {
            return false;
        }
        return containsAll(other);
    }

    public int hashCode() {
        int h = 0;
        Iterator<T> it = iterator();
        while (it.hasNext()) {
            Object x = it.next();
            if (x != null) {
                h = h + x.hashCode();
            }
        }
        return h;
    }
}

class ArrayDeque<T> {
    T[] storage;
    int head;
    int tail;
    int size;

    ArrayDeque() {
        this.storage = new T[8];
        this.head = 0;
        this.tail = 0;
        this.size = 0;
    }

    void grow() {
        int oldCap = this.storage.length;
        int newCap = oldCap + oldCap;
        T[] neu = new T[newCap];
        int i = 0;
        while (i < this.size) {
            int idx = this.head + i;
            if (idx >= oldCap) {
                idx = idx - oldCap;
            }
            neu[i] = this.storage[idx];
            i = i + 1;
        }
        this.storage = neu;
        this.head = 0;
        this.tail = this.size;
    }

    public int size() {
        return this.size;
    }

    public boolean isEmpty() {
        return this.size == 0;
    }

    public void addLast(T value) {
        if (this.size == this.storage.length) {
            grow();
        }
        this.storage[this.tail] = value;
        this.tail = this.tail + 1;
        if (this.tail >= this.storage.length) {
            this.tail = 0;
        }
        this.size = this.size + 1;
    }

    public void addFirst(T value) {
        if (this.size == this.storage.length) {
            grow();
        }
        this.head = this.head - 1;
        if (this.head < 0) {
            this.head = this.storage.length - 1;
        }
        this.storage[this.head] = value;
        this.size = this.size + 1;
    }

    public T removeFirst() {
        if (this.size == 0) {
            throw new NoSuchElementException("ArrayDeque");
        }
        T value = this.storage[this.head];
        this.storage[this.head] = null;
        this.head = this.head + 1;
        if (this.head >= this.storage.length) {
            this.head = 0;
        }
        this.size = this.size - 1;
        return value;
    }

    public T removeLast() {
        if (this.size == 0) {
            throw new NoSuchElementException("ArrayDeque");
        }
        this.tail = this.tail - 1;
        if (this.tail < 0) {
            this.tail = this.storage.length - 1;
        }
        T value = this.storage[this.tail];
        this.storage[this.tail] = null;
        this.size = this.size - 1;
        return value;
    }

    public T peekFirst() {
        if (this.size == 0) {
            return null;
        }
        return this.storage[this.head];
    }

    public T peekLast() {
        if (this.size == 0) {
            return null;
        }
        int idx = this.tail - 1;
        if (idx < 0) {
            idx = this.storage.length - 1;
        }
        return this.storage[idx];
    }

    /** Queue-compatible alias for addLast. */
    public boolean offer(T value) {
        addLast(value);
        return true;
    }
}

/**
 * StringBuilder (Round 1): chunk list + join at toString.
 * Avoids byte[] / primitive casts (not yet reliable). Chunking keeps append
 * amortized better than naive data = data + s for many small appends.
 */
class StringBuilder {
    ArrayList<String> parts;
    int length;

    StringBuilder() {
        this.parts = new ArrayList<String>();
        this.length = 0;
    }

    public StringBuilder append(String s) {
        if (s == null) {
            s = "null";
        }
        this.parts.add(s);
        this.length = this.length + s.length();
        return this;
    }

    public StringBuilder appendInt(int v) {
        return append(String.valueOf(v));
    }

    /** Appends one UTF-16 code unit (Java: {@code append(char)}). */
    public StringBuilder append(char c) {
        return append(String.valueOf(c));
    }

    /** Appends the decimal digits of {@code v} (Java: {@code append(int)}). */
    public StringBuilder append(int v) {
        return append(String.valueOf(v));
    }

    public boolean isEmpty() {
        return this.length == 0;
    }

    public char charAt(int index) {
        if (index < 0 || index >= this.length) {
            throw new IndexOutOfBoundsException("StringBuilder.charAt");
        }
        return toString().charAt(index);
    }

    public StringBuilder setCharAt(int index, char c) {
        if (index < 0 || index >= this.length) {
            throw new IndexOutOfBoundsException("StringBuilder.setCharAt");
        }
        String all = toString();
        String left = all.substring(0, index);
        String right = all.substringFrom(index + 1);
        this.parts.clear();
        this.parts.add(left);
        this.parts.add(String.valueOf(c));
        this.parts.add(right);
        return this;
    }

    public StringBuilder deleteCharAt(int index) {
        if (index < 0 || index >= this.length) {
            throw new IndexOutOfBoundsException("StringBuilder.deleteCharAt");
        }
        return delete(index, index + 1);
    }

    public int length() {
        return this.length;
    }

    public void setLength(int newLength) {
        if (newLength < 0) {
            throw new IndexOutOfBoundsException("StringBuilder.setLength");
        }
        if (newLength == 0) {
            this.parts.clear();
            this.length = 0;
            return;
        }
        if (newLength >= this.length) {
            return;
        }
        String all = toString();
        this.parts.clear();
        this.parts.add(all.substring(0, newLength));
        this.length = newLength;
    }

    public StringBuilder reverse() {
        String all = toString();
        StringBuilder rev = new StringBuilder();
        int i = all.length();
        while (i > 0) {
            rev.append(all.substring(i - 1, i));
            i = i - 1;
        }
        this.parts.clear();
        this.parts.add(rev.toString());
        this.length = all.length();
        return this;
    }

    public StringBuilder delete(int start, int end) {
        String all = toString();
        if (start < 0 || end > all.length() || start > end) {
            throw new IndexOutOfBoundsException("StringBuilder.delete");
        }
        String left = all.substring(0, start);
        String right = all.substringFrom(end);
        this.parts.clear();
        this.parts.add(left);
        this.parts.add(right);
        this.length = left.length() + right.length();
        return this;
    }

    public StringBuilder insert(int offset, String s) {
        String all = toString();
        if (offset < 0 || offset > all.length()) {
            throw new IndexOutOfBoundsException("StringBuilder.insert");
        }
        if (s == null) {
            s = "null";
        }
        String left = all.substring(0, offset);
        String right = all.substringFrom(offset);
        this.parts.clear();
        this.parts.add(left);
        this.parts.add(s);
        this.parts.add(right);
        this.length = left.length() + s.length() + right.length();
        return this;
    }

    public String toString() {
        String out = "";
        int i = 0;
        while (i < this.parts.size()) {
            out = out + this.parts.get(i);
            i = i + 1;
        }
        return out;
    }
}

interface IndexCmp {
    int compare(int i, int j);
}

class SortSupport {
    static int natural(Object a, Object b) {
        if (a instanceof String) {
            return ((String) a).compareTo((String) b);
        }
        if (a instanceof Integer) {
            return ((Integer) a).compareTo((Integer) b);
        }
        if (a instanceof Long) {
            return ((Long) a).compareTo((Long) b);
        }
        if (a instanceof Double) {
            return ((Double) a).compareTo((Double) b);
        }
        if (a instanceof Character) {
            return ((Character) a).compareTo((Character) b);
        }
        if (a instanceof Comparable) {
            Comparable<?> c = (Comparable<?>) a;
            return c.compareTo(b);
        }
        throw new ClassCastException("not Comparable");
    }

    static <T> int compareWith(Comparator<T> c, T a, T b) {
        if (c == null) {
            return natural(a, b);
        }
        return c.compare(a, b);
    }

    /** Stable merge sort of positions 0..n-1 under {@code cmp}; returns the sorted permutation. */
    static int[] sortedOrder(int n, IndexCmp cmp) {
        int[] order = new int[n];
        int[] tmp = new int[n];
        int i = 0;
        while (i < n) {
            order[i] = i;
            i = i + 1;
        }
        mergeOrder(order, tmp, 0, n, cmp);
        return order;
    }

    static void mergeOrder(int[] a, int[] tmp, int from, int to, IndexCmp cmp) {
        int n = to - from;
        if (n < 2) {
            return;
        }
        if (n < 7) {
            int i = from + 1;
            while (i < to) {
                int v = a[i];
                int j = i - 1;
                while (j >= from && cmp.compare(a[j], v) > 0) {
                    a[j + 1] = a[j];
                    j = j - 1;
                }
                a[j + 1] = v;
                i = i + 1;
            }
            return;
        }
        int mid = from + n / 2;
        mergeOrder(a, tmp, from, mid, cmp);
        mergeOrder(a, tmp, mid, to, cmp);
        if (cmp.compare(a[mid - 1], a[mid]) <= 0) {
            return;
        }
        int p = from;
        int q = mid;
        int k = from;
        while (p < mid && q < to) {
            if (cmp.compare(a[q], a[p]) < 0) {
                tmp[k] = a[q];
                q = q + 1;
            } else {
                tmp[k] = a[p];
                p = p + 1;
            }
            k = k + 1;
        }
        while (p < mid) {
            tmp[k] = a[p];
            p = p + 1;
            k = k + 1;
        }
        while (q < to) {
            tmp[k] = a[q];
            q = q + 1;
            k = k + 1;
        }
        int m = from;
        while (m < to) {
            a[m] = tmp[m];
            m = m + 1;
        }
    }
}
class Arrays {
    static <T> T[] copyOf(T[] original, int newLength) {
        if (newLength < 0) {
            throw new NegativeArraySizeException("copyOf");
        }
        T[] result = new T[newLength];
        int n = original.length;
        if (n > newLength) {
            n = newLength;
        }
        int i = 0;
        while (i < n) {
            result[i] = original[i];
            i = i + 1;
        }
        return result;
    }

    static <T> T[] copyOfRange(T[] original, int from, int to) {
        if (from > to) {
            throw new IllegalArgumentException("from > to");
        }
        if (from < 0 || from > original.length) {
            throw new ArrayIndexOutOfBoundsException("copyOfRange");
        }
        T[] result = new T[to - from];
        int i = from;
        while (i < to && i < original.length) {
            result[i - from] = original[i];
            i = i + 1;
        }
        return result;
    }

    static void fill(Object[] a, Object value) {
        int i = 0;
        while (i < a.length) {
            a[i] = value;
            i = i + 1;
        }
    }

    static boolean arraysEqual(Object[] a, Object[] b) {
        return equals(a, b);
    }

    static boolean equals(Object[] a, Object[] b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (a.length != b.length) {
            return false;
        }
        int i = 0;
        while (i < a.length) {
            Object x = a[i];
            Object y = b[i];
            if (x != y) {
                if (x == null || y == null) {
                    return false;
                }
                if (!x.equals(y)) {
                    return false;
                }
            }
            i = i + 1;
        }
        return true;
    }

    static int hashCode(Object[] a) {
        if (a == null) {
            return 0;
        }
        int h = 1;
        int i = 0;
        while (i < a.length) {
            Object x = a[i];
            int eh = 0;
            if (x != null) {
                eh = x.hashCode();
            }
            h = 31 * h + eh;
            i = i + 1;
        }
        return h;
    }

    static String toString(Object[] a) {
        if (a == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        int i = 0;
        while (i < a.length) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(String.valueOf(a[i]));
            i = i + 1;
        }
        sb.append("]");
        return sb.toString();
    }

    static void sort(Object[] a) {
        sortRange(a, 0, a.length);
    }

    static void sortRange(Object[] a, int from, int to) {
        sortWith(a, from, to, null);
    }

    static <T> void sort(T[] a, Comparator<T> c) {
        sortWith(a, 0, a.length, c);
    }

    static <T> void sortWith(T[] a, int from, int to, Comparator<T> c) {
        if (from < 0 || to > a.length || from > to) {
            throw new ArrayIndexOutOfBoundsException("sort range");
        }
        int n = to - from;
        if (n < 2) {
            return;
        }
        int[] order = SortSupport.sortedOrder(n, (i, j) -> SortSupport.compareWith(c, a[from + i], a[from + j]));
        T[] copy = new T[n];
        int k = 0;
        while (k < n) {
            copy[k] = a[from + order[k]];
            k = k + 1;
        }
        k = 0;
        while (k < n) {
            a[from + k] = copy[k];
            k = k + 1;
        }
    }

    static <T> List<T> asList(T[] a) {
        ArrayList<T> out = new ArrayList<T>(a.length);
        int i = 0;
        while (i < a.length) {
            out.add(a[i]);
            i = i + 1;
        }
        return out;
    }
    static void fill(int[] a, int value) {
        int i = 0;
        while (i < a.length) {
            a[i] = value;
            i = i + 1;
        }
    }

    static int[] copyOf(int[] original, int newLength) {
        if (newLength < 0) {
            throw new NegativeArraySizeException("copyOf");
        }
        int[] result = new int[newLength];
        int n = original.length;
        if (n > newLength) {
            n = newLength;
        }
        int i = 0;
        while (i < n) {
            result[i] = original[i];
            i = i + 1;
        }
        return result;
    }

    static int[] copyOfRange(int[] original, int from, int to) {
        if (from > to) {
            throw new IllegalArgumentException("from > to");
        }
        if (from < 0 || from > original.length) {
            throw new ArrayIndexOutOfBoundsException("copyOfRange");
        }
        int[] result = new int[to - from];
        int i = from;
        while (i < to && i < original.length) {
            result[i - from] = original[i];
            i = i + 1;
        }
        return result;
    }

    static boolean equals(int[] a, int[] b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (a.length != b.length) {
            return false;
        }
        int i = 0;
        while (i < a.length) {
            if (a[i] != b[i]) {
                return false;
            }
            i = i + 1;
        }
        return true;
    }

    static String toString(int[] a) {
        if (a == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        int i = 0;
        while (i < a.length) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(String.valueOf(a[i]));
            i = i + 1;
        }
        sb.append("]");
        return sb.toString();
    }
    static void fill(long[] a, long value) {
        int i = 0;
        while (i < a.length) {
            a[i] = value;
            i = i + 1;
        }
    }

    static long[] copyOf(long[] original, int newLength) {
        if (newLength < 0) {
            throw new NegativeArraySizeException("copyOf");
        }
        long[] result = new long[newLength];
        int n = original.length;
        if (n > newLength) {
            n = newLength;
        }
        int i = 0;
        while (i < n) {
            result[i] = original[i];
            i = i + 1;
        }
        return result;
    }

    static long[] copyOfRange(long[] original, int from, int to) {
        if (from > to) {
            throw new IllegalArgumentException("from > to");
        }
        if (from < 0 || from > original.length) {
            throw new ArrayIndexOutOfBoundsException("copyOfRange");
        }
        long[] result = new long[to - from];
        int i = from;
        while (i < to && i < original.length) {
            result[i - from] = original[i];
            i = i + 1;
        }
        return result;
    }

    static boolean equals(long[] a, long[] b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (a.length != b.length) {
            return false;
        }
        int i = 0;
        while (i < a.length) {
            if (a[i] != b[i]) {
                return false;
            }
            i = i + 1;
        }
        return true;
    }

    static String toString(long[] a) {
        if (a == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        int i = 0;
        while (i < a.length) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(String.valueOf(a[i]));
            i = i + 1;
        }
        sb.append("]");
        return sb.toString();
    }
    static void fill(double[] a, double value) {
        int i = 0;
        while (i < a.length) {
            a[i] = value;
            i = i + 1;
        }
    }

    static double[] copyOf(double[] original, int newLength) {
        if (newLength < 0) {
            throw new NegativeArraySizeException("copyOf");
        }
        double[] result = new double[newLength];
        int n = original.length;
        if (n > newLength) {
            n = newLength;
        }
        int i = 0;
        while (i < n) {
            result[i] = original[i];
            i = i + 1;
        }
        return result;
    }

    static double[] copyOfRange(double[] original, int from, int to) {
        if (from > to) {
            throw new IllegalArgumentException("from > to");
        }
        if (from < 0 || from > original.length) {
            throw new ArrayIndexOutOfBoundsException("copyOfRange");
        }
        double[] result = new double[to - from];
        int i = from;
        while (i < to && i < original.length) {
            result[i - from] = original[i];
            i = i + 1;
        }
        return result;
    }

    static boolean equals(double[] a, double[] b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (a.length != b.length) {
            return false;
        }
        int i = 0;
        while (i < a.length) {
            if (a[i] != b[i]) {
                return false;
            }
            i = i + 1;
        }
        return true;
    }

    static String toString(double[] a) {
        if (a == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        int i = 0;
        while (i < a.length) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(String.valueOf(a[i]));
            i = i + 1;
        }
        sb.append("]");
        return sb.toString();
    }
    static void fill(char[] a, char value) {
        int i = 0;
        while (i < a.length) {
            a[i] = value;
            i = i + 1;
        }
    }

    static char[] copyOf(char[] original, int newLength) {
        if (newLength < 0) {
            throw new NegativeArraySizeException("copyOf");
        }
        char[] result = new char[newLength];
        int n = original.length;
        if (n > newLength) {
            n = newLength;
        }
        int i = 0;
        while (i < n) {
            result[i] = original[i];
            i = i + 1;
        }
        return result;
    }

    static char[] copyOfRange(char[] original, int from, int to) {
        if (from > to) {
            throw new IllegalArgumentException("from > to");
        }
        if (from < 0 || from > original.length) {
            throw new ArrayIndexOutOfBoundsException("copyOfRange");
        }
        char[] result = new char[to - from];
        int i = from;
        while (i < to && i < original.length) {
            result[i - from] = original[i];
            i = i + 1;
        }
        return result;
    }

    static boolean equals(char[] a, char[] b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (a.length != b.length) {
            return false;
        }
        int i = 0;
        while (i < a.length) {
            if (a[i] != b[i]) {
                return false;
            }
            i = i + 1;
        }
        return true;
    }

    static String toString(char[] a) {
        if (a == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        int i = 0;
        while (i < a.length) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(String.valueOf(a[i]));
            i = i + 1;
        }
        sb.append("]");
        return sb.toString();
    }
    static void fill(boolean[] a, boolean value) {
        int i = 0;
        while (i < a.length) {
            a[i] = value;
            i = i + 1;
        }
    }

    static boolean[] copyOf(boolean[] original, int newLength) {
        if (newLength < 0) {
            throw new NegativeArraySizeException("copyOf");
        }
        boolean[] result = new boolean[newLength];
        int n = original.length;
        if (n > newLength) {
            n = newLength;
        }
        int i = 0;
        while (i < n) {
            result[i] = original[i];
            i = i + 1;
        }
        return result;
    }

    static boolean[] copyOfRange(boolean[] original, int from, int to) {
        if (from > to) {
            throw new IllegalArgumentException("from > to");
        }
        if (from < 0 || from > original.length) {
            throw new ArrayIndexOutOfBoundsException("copyOfRange");
        }
        boolean[] result = new boolean[to - from];
        int i = from;
        while (i < to && i < original.length) {
            result[i - from] = original[i];
            i = i + 1;
        }
        return result;
    }

    static boolean equals(boolean[] a, boolean[] b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (a.length != b.length) {
            return false;
        }
        int i = 0;
        while (i < a.length) {
            if (a[i] != b[i]) {
                return false;
            }
            i = i + 1;
        }
        return true;
    }

    static String toString(boolean[] a) {
        if (a == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        int i = 0;
        while (i < a.length) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(String.valueOf(a[i]));
            i = i + 1;
        }
        sb.append("]");
        return sb.toString();
    }
    static void sort(int[] a) {
        sort(a, 0, a.length);
    }

    static void sort(int[] a, int from, int to) {
        if (from < 0 || to > a.length || from > to) {
            throw new ArrayIndexOutOfBoundsException("sort range");
        }
        int n = to - from;
        if (n < 2) {
            return;
        }
        int[] tmp = new int[n];
        int width = 1;
        while (width < n) {
            int lo = 0;
            while (lo < n) {
                int mid = lo + width;
                int hi = lo + width + width;
                if (mid > n) {
                    mid = n;
                }
                if (hi > n) {
                    hi = n;
                }
                int p = lo;
                int q = mid;
                int k = lo;
                while (p < mid && q < hi) {
                    if (a[from + q] < a[from + p]) {
                        tmp[k] = a[from + q];
                        q = q + 1;
                    } else {
                        tmp[k] = a[from + p];
                        p = p + 1;
                    }
                    k = k + 1;
                }
                while (p < mid) {
                    tmp[k] = a[from + p];
                    p = p + 1;
                    k = k + 1;
                }
                while (q < hi) {
                    tmp[k] = a[from + q];
                    q = q + 1;
                    k = k + 1;
                }
                lo = lo + width + width;
            }
            int m = 0;
            while (m < n) {
                a[from + m] = tmp[m];
                m = m + 1;
            }
            width = width + width;
        }
    }
    static void sort(long[] a) {
        sort(a, 0, a.length);
    }

    static void sort(long[] a, int from, int to) {
        if (from < 0 || to > a.length || from > to) {
            throw new ArrayIndexOutOfBoundsException("sort range");
        }
        int n = to - from;
        if (n < 2) {
            return;
        }
        long[] tmp = new long[n];
        int width = 1;
        while (width < n) {
            int lo = 0;
            while (lo < n) {
                int mid = lo + width;
                int hi = lo + width + width;
                if (mid > n) {
                    mid = n;
                }
                if (hi > n) {
                    hi = n;
                }
                int p = lo;
                int q = mid;
                int k = lo;
                while (p < mid && q < hi) {
                    if (a[from + q] < a[from + p]) {
                        tmp[k] = a[from + q];
                        q = q + 1;
                    } else {
                        tmp[k] = a[from + p];
                        p = p + 1;
                    }
                    k = k + 1;
                }
                while (p < mid) {
                    tmp[k] = a[from + p];
                    p = p + 1;
                    k = k + 1;
                }
                while (q < hi) {
                    tmp[k] = a[from + q];
                    q = q + 1;
                    k = k + 1;
                }
                lo = lo + width + width;
            }
            int m = 0;
            while (m < n) {
                a[from + m] = tmp[m];
                m = m + 1;
            }
            width = width + width;
        }
    }
    static void sort(double[] a) {
        sort(a, 0, a.length);
    }

    static void sort(double[] a, int from, int to) {
        if (from < 0 || to > a.length || from > to) {
            throw new ArrayIndexOutOfBoundsException("sort range");
        }
        int n = to - from;
        if (n < 2) {
            return;
        }
        double[] tmp = new double[n];
        int width = 1;
        while (width < n) {
            int lo = 0;
            while (lo < n) {
                int mid = lo + width;
                int hi = lo + width + width;
                if (mid > n) {
                    mid = n;
                }
                if (hi > n) {
                    hi = n;
                }
                int p = lo;
                int q = mid;
                int k = lo;
                while (p < mid && q < hi) {
                    if (a[from + q] < a[from + p]) {
                        tmp[k] = a[from + q];
                        q = q + 1;
                    } else {
                        tmp[k] = a[from + p];
                        p = p + 1;
                    }
                    k = k + 1;
                }
                while (p < mid) {
                    tmp[k] = a[from + p];
                    p = p + 1;
                    k = k + 1;
                }
                while (q < hi) {
                    tmp[k] = a[from + q];
                    q = q + 1;
                    k = k + 1;
                }
                lo = lo + width + width;
            }
            int m = 0;
            while (m < n) {
                a[from + m] = tmp[m];
                m = m + 1;
            }
            width = width + width;
        }
    }
    static void sort(char[] a) {
        sort(a, 0, a.length);
    }

    static void sort(char[] a, int from, int to) {
        if (from < 0 || to > a.length || from > to) {
            throw new ArrayIndexOutOfBoundsException("sort range");
        }
        int n = to - from;
        if (n < 2) {
            return;
        }
        char[] tmp = new char[n];
        int width = 1;
        while (width < n) {
            int lo = 0;
            while (lo < n) {
                int mid = lo + width;
                int hi = lo + width + width;
                if (mid > n) {
                    mid = n;
                }
                if (hi > n) {
                    hi = n;
                }
                int p = lo;
                int q = mid;
                int k = lo;
                while (p < mid && q < hi) {
                    if (a[from + q] < a[from + p]) {
                        tmp[k] = a[from + q];
                        q = q + 1;
                    } else {
                        tmp[k] = a[from + p];
                        p = p + 1;
                    }
                    k = k + 1;
                }
                while (p < mid) {
                    tmp[k] = a[from + p];
                    p = p + 1;
                    k = k + 1;
                }
                while (q < hi) {
                    tmp[k] = a[from + q];
                    q = q + 1;
                    k = k + 1;
                }
                lo = lo + width + width;
            }
            int m = 0;
            while (m < n) {
                a[from + m] = tmp[m];
                m = m + 1;
            }
            width = width + width;
        }
    }
}

class Collections {
    static <T> void swap(List<T> list, int i, int j) {
        T a = list.get(i);
        T b = list.get(j);
        list.set(i, b);
        list.set(j, a);
    }

    static <T> void reverse(List<T> list) {
        int i = 0;
        int j = list.size() - 1;
        while (i < j) {
            T a = list.get(i);
            T b = list.get(j);
            list.set(i, b);
            list.set(j, a);
            i = i + 1;
            j = j - 1;
        }
    }

    static <T> void sort(List<T> list) {
        list.sort(null);
    }

    static <T> void sort(List<T> list, Comparator<T> c) {
        list.sort(c);
    }

    static <T> T max(Collection<T> c) {
        return maxWith(c, null);
    }

    static <T> T max(Collection<T> c, Comparator<T> cmp) {
        return maxWith(c, cmp);
    }

    static <T> T min(Collection<T> c) {
        return minWith(c, null);
    }

    static <T> T min(Collection<T> c, Comparator<T> cmp) {
        return minWith(c, cmp);
    }

    static <T> T maxWith(Collection<T> c, Comparator<T> cmp) {
        Iterator<T> it = c.iterator();
        T best = it.next();
        while (it.hasNext()) {
            T v = it.next();
            if (SortSupport.compareWith(cmp, v, best) > 0) {
                best = v;
            }
        }
        return best;
    }

    static <T> T minWith(Collection<T> c, Comparator<T> cmp) {
        Iterator<T> it = c.iterator();
        T best = it.next();
        while (it.hasNext()) {
            T v = it.next();
            if (SortSupport.compareWith(cmp, v, best) < 0) {
                best = v;
            }
        }
        return best;
    }

    static <T> Comparator<T> reverseOrder() {
        return (a, b) -> SortSupport.natural(b, a);
    }

    static <T> Comparator<T> reverseOrder(Comparator<T> c) {
        return (a, b) -> SortSupport.compareWith(c, b, a);
    }

    static <T> int frequency(Collection<T> c, Object o) {
        int n = 0;
        Iterator<T> it = c.iterator();
        while (it.hasNext()) {
            Object x = it.next();
            if (x == o || (x != null && x.equals(o))) {
                n = n + 1;
            }
        }
        return n;
    }

    static <T> int binarySearch(List<T> list, T key) {
        int lo = 0;
        int hi = list.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int c = SortSupport.natural(list.get(mid), key);
            if (c < 0) {
                lo = mid + 1;
            } else if (c > 0) {
                hi = mid - 1;
            } else {
                return mid;
            }
        }
        return -(lo + 1);
    }

    static <T> List<T> emptyList() {
        return new ArrayList<T>();
    }
}
/** Desugaring targets for List.of / Arrays.asList / List.copyOf (mutable lists; see docs). */
class JnLists {
    static <T> List<T> copyOf(Collection<T> c) {
        return new ArrayList<T>(c);
    }

    static <T> List<T> of() {
        return new ArrayList<T>(0);
    }

    static <T> List<T> of(T a1) {
        ArrayList<T> l = new ArrayList<T>(1);
        l.add(a1);
        return l;
    }

    static <T> List<T> of(T a1, T a2) {
        ArrayList<T> l = new ArrayList<T>(2);
        l.add(a1);
        l.add(a2);
        return l;
    }

    static <T> List<T> of(T a1, T a2, T a3) {
        ArrayList<T> l = new ArrayList<T>(3);
        l.add(a1);
        l.add(a2);
        l.add(a3);
        return l;
    }

    static <T> List<T> of(T a1, T a2, T a3, T a4) {
        ArrayList<T> l = new ArrayList<T>(4);
        l.add(a1);
        l.add(a2);
        l.add(a3);
        l.add(a4);
        return l;
    }

    static <T> List<T> of(T a1, T a2, T a3, T a4, T a5) {
        ArrayList<T> l = new ArrayList<T>(5);
        l.add(a1);
        l.add(a2);
        l.add(a3);
        l.add(a4);
        l.add(a5);
        return l;
    }

    static <T> List<T> of(T a1, T a2, T a3, T a4, T a5, T a6) {
        ArrayList<T> l = new ArrayList<T>(6);
        l.add(a1);
        l.add(a2);
        l.add(a3);
        l.add(a4);
        l.add(a5);
        l.add(a6);
        return l;
    }

    static <T> List<T> of(T a1, T a2, T a3, T a4, T a5, T a6, T a7) {
        ArrayList<T> l = new ArrayList<T>(7);
        l.add(a1);
        l.add(a2);
        l.add(a3);
        l.add(a4);
        l.add(a5);
        l.add(a6);
        l.add(a7);
        return l;
    }

    static <T> List<T> of(T a1, T a2, T a3, T a4, T a5, T a6, T a7, T a8) {
        ArrayList<T> l = new ArrayList<T>(8);
        l.add(a1);
        l.add(a2);
        l.add(a3);
        l.add(a4);
        l.add(a5);
        l.add(a6);
        l.add(a7);
        l.add(a8);
        return l;
    }

    static <T> List<T> of(T a1, T a2, T a3, T a4, T a5, T a6, T a7, T a8, T a9) {
        ArrayList<T> l = new ArrayList<T>(9);
        l.add(a1);
        l.add(a2);
        l.add(a3);
        l.add(a4);
        l.add(a5);
        l.add(a6);
        l.add(a7);
        l.add(a8);
        l.add(a9);
        return l;
    }

    static <T> List<T> of(T a1, T a2, T a3, T a4, T a5, T a6, T a7, T a8, T a9, T a10) {
        ArrayList<T> l = new ArrayList<T>(10);
        l.add(a1);
        l.add(a2);
        l.add(a3);
        l.add(a4);
        l.add(a5);
        l.add(a6);
        l.add(a7);
        l.add(a8);
        l.add(a9);
        l.add(a10);
        return l;
    }

}

/** Desugaring targets for Set.of / Set.copyOf. */
class JnSets {
    static <T> Set<T> copyOf(Collection<T> c) {
        return new HashSet<T>(c);
    }

    static <T> Set<T> of() {
        return new HashSet<T>();
    }

    static <T> Set<T> of(T a1) {
        HashSet<T> s = new HashSet<T>();
        if (!s.add(a1)) {
            throw new IllegalArgumentException("duplicate element");
        }
        return s;
    }

    static <T> Set<T> of(T a1, T a2) {
        HashSet<T> s = new HashSet<T>();
        if (!s.add(a1)) {
            throw new IllegalArgumentException("duplicate element");
        }
        if (!s.add(a2)) {
            throw new IllegalArgumentException("duplicate element");
        }
        return s;
    }

    static <T> Set<T> of(T a1, T a2, T a3) {
        HashSet<T> s = new HashSet<T>();
        if (!s.add(a1)) {
            throw new IllegalArgumentException("duplicate element");
        }
        if (!s.add(a2)) {
            throw new IllegalArgumentException("duplicate element");
        }
        if (!s.add(a3)) {
            throw new IllegalArgumentException("duplicate element");
        }
        return s;
    }

    static <T> Set<T> of(T a1, T a2, T a3, T a4) {
        HashSet<T> s = new HashSet<T>();
        if (!s.add(a1)) {
            throw new IllegalArgumentException("duplicate element");
        }
        if (!s.add(a2)) {
            throw new IllegalArgumentException("duplicate element");
        }
        if (!s.add(a3)) {
            throw new IllegalArgumentException("duplicate element");
        }
        if (!s.add(a4)) {
            throw new IllegalArgumentException("duplicate element");
        }
        return s;
    }

    static <T> Set<T> of(T a1, T a2, T a3, T a4, T a5) {
        HashSet<T> s = new HashSet<T>();
        if (!s.add(a1)) {
            throw new IllegalArgumentException("duplicate element");
        }
        if (!s.add(a2)) {
            throw new IllegalArgumentException("duplicate element");
        }
        if (!s.add(a3)) {
            throw new IllegalArgumentException("duplicate element");
        }
        if (!s.add(a4)) {
            throw new IllegalArgumentException("duplicate element");
        }
        if (!s.add(a5)) {
            throw new IllegalArgumentException("duplicate element");
        }
        return s;
    }

    static <T> Set<T> of(T a1, T a2, T a3, T a4, T a5, T a6) {
        HashSet<T> s = new HashSet<T>();
        if (!s.add(a1)) {
            throw new IllegalArgumentException("duplicate element");
        }
        if (!s.add(a2)) {
            throw new IllegalArgumentException("duplicate element");
        }
        if (!s.add(a3)) {
            throw new IllegalArgumentException("duplicate element");
        }
        if (!s.add(a4)) {
            throw new IllegalArgumentException("duplicate element");
        }
        if (!s.add(a5)) {
            throw new IllegalArgumentException("duplicate element");
        }
        if (!s.add(a6)) {
            throw new IllegalArgumentException("duplicate element");
        }
        return s;
    }

    static <T> Set<T> of(T a1, T a2, T a3, T a4, T a5, T a6, T a7) {
        HashSet<T> s = new HashSet<T>();
        if (!s.add(a1)) {
            throw new IllegalArgumentException("duplicate element");
        }
        if (!s.add(a2)) {
            throw new IllegalArgumentException("duplicate element");
        }
        if (!s.add(a3)) {
            throw new IllegalArgumentException("duplicate element");
        }
        if (!s.add(a4)) {
            throw new IllegalArgumentException("duplicate element");
        }
        if (!s.add(a5)) {
            throw new IllegalArgumentException("duplicate element");
        }
        if (!s.add(a6)) {
            throw new IllegalArgumentException("duplicate element");
        }
        if (!s.add(a7)) {
            throw new IllegalArgumentException("duplicate element");
        }
        return s;
    }

    static <T> Set<T> of(T a1, T a2, T a3, T a4, T a5, T a6, T a7, T a8) {
        HashSet<T> s = new HashSet<T>();
        if (!s.add(a1)) {
            throw new IllegalArgumentException("duplicate element");
        }
        if (!s.add(a2)) {
            throw new IllegalArgumentException("duplicate element");
        }
        if (!s.add(a3)) {
            throw new IllegalArgumentException("duplicate element");
        }
        if (!s.add(a4)) {
            throw new IllegalArgumentException("duplicate element");
        }
        if (!s.add(a5)) {
            throw new IllegalArgumentException("duplicate element");
        }
        if (!s.add(a6)) {
            throw new IllegalArgumentException("duplicate element");
        }
        if (!s.add(a7)) {
            throw new IllegalArgumentException("duplicate element");
        }
        if (!s.add(a8)) {
            throw new IllegalArgumentException("duplicate element");
        }
        return s;
    }

    static <T> Set<T> of(T a1, T a2, T a3, T a4, T a5, T a6, T a7, T a8, T a9) {
        HashSet<T> s = new HashSet<T>();
        if (!s.add(a1)) {
            throw new IllegalArgumentException("duplicate element");
        }
        if (!s.add(a2)) {
            throw new IllegalArgumentException("duplicate element");
        }
        if (!s.add(a3)) {
            throw new IllegalArgumentException("duplicate element");
        }
        if (!s.add(a4)) {
            throw new IllegalArgumentException("duplicate element");
        }
        if (!s.add(a5)) {
            throw new IllegalArgumentException("duplicate element");
        }
        if (!s.add(a6)) {
            throw new IllegalArgumentException("duplicate element");
        }
        if (!s.add(a7)) {
            throw new IllegalArgumentException("duplicate element");
        }
        if (!s.add(a8)) {
            throw new IllegalArgumentException("duplicate element");
        }
        if (!s.add(a9)) {
            throw new IllegalArgumentException("duplicate element");
        }
        return s;
    }

    static <T> Set<T> of(T a1, T a2, T a3, T a4, T a5, T a6, T a7, T a8, T a9, T a10) {
        HashSet<T> s = new HashSet<T>();
        if (!s.add(a1)) {
            throw new IllegalArgumentException("duplicate element");
        }
        if (!s.add(a2)) {
            throw new IllegalArgumentException("duplicate element");
        }
        if (!s.add(a3)) {
            throw new IllegalArgumentException("duplicate element");
        }
        if (!s.add(a4)) {
            throw new IllegalArgumentException("duplicate element");
        }
        if (!s.add(a5)) {
            throw new IllegalArgumentException("duplicate element");
        }
        if (!s.add(a6)) {
            throw new IllegalArgumentException("duplicate element");
        }
        if (!s.add(a7)) {
            throw new IllegalArgumentException("duplicate element");
        }
        if (!s.add(a8)) {
            throw new IllegalArgumentException("duplicate element");
        }
        if (!s.add(a9)) {
            throw new IllegalArgumentException("duplicate element");
        }
        if (!s.add(a10)) {
            throw new IllegalArgumentException("duplicate element");
        }
        return s;
    }

}

/** Desugaring targets for Map.of / Map.entry / Map.copyOf. */
class JnMaps {
    static <K, V> Map<K, V> copyOf(Map<K, V> m) {
        return new HashMap<K, V>(m);
    }

    static <K, V> Entry<K, V> entry(K k, V v) {
        return new HashMapEntry<K, V>(0, k, v);
    }

    static <K, V> Map<K, V> of() {
        return new HashMap<K, V>();
    }

    static <K, V> Map<K, V> of(K k1, V v1) {
        HashMap<K, V> m = new HashMap<K, V>();
        if (m.containsKey(k1)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k1, v1);
        return m;
    }

    static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2) {
        HashMap<K, V> m = new HashMap<K, V>();
        if (m.containsKey(k1)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k1, v1);
        if (m.containsKey(k2)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k2, v2);
        return m;
    }

    static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3) {
        HashMap<K, V> m = new HashMap<K, V>();
        if (m.containsKey(k1)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k1, v1);
        if (m.containsKey(k2)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k2, v2);
        if (m.containsKey(k3)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k3, v3);
        return m;
    }

    static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4) {
        HashMap<K, V> m = new HashMap<K, V>();
        if (m.containsKey(k1)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k1, v1);
        if (m.containsKey(k2)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k2, v2);
        if (m.containsKey(k3)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k3, v3);
        if (m.containsKey(k4)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k4, v4);
        return m;
    }

    static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5) {
        HashMap<K, V> m = new HashMap<K, V>();
        if (m.containsKey(k1)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k1, v1);
        if (m.containsKey(k2)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k2, v2);
        if (m.containsKey(k3)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k3, v3);
        if (m.containsKey(k4)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k4, v4);
        if (m.containsKey(k5)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k5, v5);
        return m;
    }

    static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6) {
        HashMap<K, V> m = new HashMap<K, V>();
        if (m.containsKey(k1)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k1, v1);
        if (m.containsKey(k2)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k2, v2);
        if (m.containsKey(k3)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k3, v3);
        if (m.containsKey(k4)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k4, v4);
        if (m.containsKey(k5)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k5, v5);
        if (m.containsKey(k6)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k6, v6);
        return m;
    }

    static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6, K k7, V v7) {
        HashMap<K, V> m = new HashMap<K, V>();
        if (m.containsKey(k1)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k1, v1);
        if (m.containsKey(k2)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k2, v2);
        if (m.containsKey(k3)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k3, v3);
        if (m.containsKey(k4)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k4, v4);
        if (m.containsKey(k5)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k5, v5);
        if (m.containsKey(k6)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k6, v6);
        if (m.containsKey(k7)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k7, v7);
        return m;
    }

    static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6, K k7, V v7, K k8, V v8) {
        HashMap<K, V> m = new HashMap<K, V>();
        if (m.containsKey(k1)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k1, v1);
        if (m.containsKey(k2)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k2, v2);
        if (m.containsKey(k3)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k3, v3);
        if (m.containsKey(k4)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k4, v4);
        if (m.containsKey(k5)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k5, v5);
        if (m.containsKey(k6)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k6, v6);
        if (m.containsKey(k7)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k7, v7);
        if (m.containsKey(k8)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k8, v8);
        return m;
    }

    static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6, K k7, V v7, K k8, V v8, K k9, V v9) {
        HashMap<K, V> m = new HashMap<K, V>();
        if (m.containsKey(k1)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k1, v1);
        if (m.containsKey(k2)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k2, v2);
        if (m.containsKey(k3)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k3, v3);
        if (m.containsKey(k4)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k4, v4);
        if (m.containsKey(k5)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k5, v5);
        if (m.containsKey(k6)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k6, v6);
        if (m.containsKey(k7)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k7, v7);
        if (m.containsKey(k8)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k8, v8);
        if (m.containsKey(k9)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k9, v9);
        return m;
    }

    static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6, K k7, V v7, K k8, V v8, K k9, V v9, K k10, V v10) {
        HashMap<K, V> m = new HashMap<K, V>();
        if (m.containsKey(k1)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k1, v1);
        if (m.containsKey(k2)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k2, v2);
        if (m.containsKey(k3)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k3, v3);
        if (m.containsKey(k4)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k4, v4);
        if (m.containsKey(k5)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k5, v5);
        if (m.containsKey(k6)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k6, v6);
        if (m.containsKey(k7)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k7, v7);
        if (m.containsKey(k8)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k8, v8);
        if (m.containsKey(k9)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k9, v9);
        if (m.containsKey(k10)) {
            throw new IllegalArgumentException("duplicate key");
        }
        m.put(k10, v10);
        return m;
    }

}

/** Blocking work queue + fixed workers (0.22). Avoids builtin Runnable class. */
interface PoolTask {
    void run();
}

interface Executor {
    void execute(PoolTask task);
}

class ThreadPoolExecutor implements Executor {
    ArrayDeque<PoolTask> queue;
    Mutex lock;
    Condition notEmpty;
    int workerCount;
    int running;
    int shutdownFlag;
    Thread[] workers;

    ThreadPoolExecutor(int workers) {
        queue = new ArrayDeque<PoolTask>();
        lock = new Mutex();
        notEmpty = new Condition();
        workerCount = workers;
        if (workerCount < 1) {
            workerCount = 1;
        }
        running = 0;
        shutdownFlag = 0;
        this.workers = new Thread[workerCount];
        int i = 0;
        while (i < workerCount) {
            ThreadPoolWorker w = new ThreadPoolWorker();
            w.pool = this;
            this.workers[i] = w;
            w.start();
            i = i + 1;
        }
    }

    static ThreadPoolExecutor createDefault() {
        int n = 4;
        return new ThreadPoolExecutor(n);
    }

    public void execute(PoolTask task) {
        if (task == null) {
            return;
        }
        lock.lock();
        if (shutdownFlag != 0) {
            lock.unlock();
            throw new RuntimeException("ThreadPoolExecutor is shut down");
        }
        queue.addLast(task);
        notEmpty.signal();
        lock.unlock();
    }

    void shutdown() {
        lock.lock();
        shutdownFlag = 1;
        notEmpty.signalAll();
        lock.unlock();
    }

    void join() {
        int i = 0;
        while (i < workerCount) {
            workers[i].join();
            i = i + 1;
        }
    }

    PoolTask takeTask() {
        lock.lock();
        int waiting = 1;
        while (waiting != 0) {
            if (queue.size() > 0) {
                PoolTask t = queue.removeFirst();
                lock.unlock();
                return t;
            }
            if (shutdownFlag != 0) {
                lock.unlock();
                return null;
            }
            notEmpty.await(lock);
        }
        lock.unlock();
        return null;
    }
}

class ThreadPoolWorker extends Thread {
    ThreadPoolExecutor pool;

    public void run() {
        int alive = 1;
        while (alive != 0) {
            PoolTask t = pool.takeTask();
            if (t == null) {
                alive = 0;
            } else {
                t.run();
            }
        }
    }
}
