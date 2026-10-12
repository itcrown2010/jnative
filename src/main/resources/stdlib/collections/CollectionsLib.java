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
}

interface List<T> extends Collection<T> {
    T get(int index);
    T set(int index, T value);
    void add(T value);
    void addAt(int index, T value);
    /** Remove by index (not overloaded with value remove — avoids Integer ambiguity). */
    T removeAt(int index);
    /** Remove first equal element; returns whether an element was removed. */
    boolean removeValue(T value);
    int indexOf(T value);
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
}

interface Entry<K, V> {
    K getKey();
    V getValue();
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

    public void add(T value) {
        ensureCapacity(this.size + 1);
        this.storage[this.size] = value;
        this.size = this.size + 1;
        this.modCount = this.modCount + 1;
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

class HashMap<K, V> implements Map<K, V> {
    static int EMPTY = 0;
    static int OCCUPIED = 1;
    static int DELETED = 2;

    K[] keys;
    V[] values;
    int[] states;
    int size;
    int modCount;
    int threshold;

    HashMap() {
        this.keys = null;
        this.values = null;
        this.states = null;
        this.size = 0;
        this.modCount = 0;
        this.threshold = 0;
    }

    HashMap(int initialCapacity) {
        this.size = 0;
        this.modCount = 0;
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("negative initialCapacity");
        }
        if (initialCapacity == 0) {
            this.keys = null;
            this.values = null;
            this.states = null;
            this.threshold = 0;
        } else {
            int cap = initialCapacity;
            if (cap < 4) {
                cap = 4;
            }
            allocTable(cap);
        }
    }

    static int spreadHash(int h) {
        // Mix without bitwise ops (language has no &/^/>>> yet).
        int x = h * 16777619;
        if (x < 0) {
            x = -x;
        }
        if (x < 0) {
            x = 0;
        }
        return x;
    }

    void allocTable(int capacity) {
        this.keys = new K[capacity];
        this.values = new V[capacity];
        this.states = new int[capacity];
        this.threshold = (capacity * 3) / 4;
    }

    int capacity() {
        if (this.states == null) {
            return 0;
        }
        return this.states.length;
    }

    int indexFor(int hash, int cap) {
        int idx = hash % cap;
        if (idx < 0) {
            idx = -idx;
        }
        if (idx >= cap) {
            idx = idx % cap;
        }
        return idx;
    }

    boolean keysEqual(K a, K b) {
        // Null keys unsupported in 0.16 (also keeps primitive specialization free of null checks).
        if (a == b) {
            return true;
        }
        return a.equals(b);
    }

    void resize() {
        int oldCap = capacity();
        int newCap = oldCap + oldCap;
        if (newCap < 8) {
            newCap = 8;
        }
        K[] oldKeys = this.keys;
        V[] oldValues = this.values;
        int[] oldStates = this.states;
        allocTable(newCap);
        this.size = 0;
        int i = 0;
        while (i < oldCap) {
            if (oldStates[i] == OCCUPIED) {
                putInsert(oldKeys[i], oldValues[i]);
            }
            i = i + 1;
        }
    }

    void putInsert(K key, V value) {
        int cap = capacity();
        int h = spreadHash(key.hashCode());
        int idx = indexFor(h, cap);
        int firstDeleted = -1;
        int probed = 0;
        while (probed < cap) {
            int st = this.states[idx];
            if (st == EMPTY) {
                int dest = idx;
                if (firstDeleted >= 0) {
                    dest = firstDeleted;
                }
                this.keys[dest] = key;
                this.values[dest] = value;
                this.states[dest] = OCCUPIED;
                this.size = this.size + 1;
                return;
            }
            if (st == DELETED) {
                if (firstDeleted < 0) {
                    firstDeleted = idx;
                }
            } else {
                if (keysEqual(this.keys[idx], key)) {
                    this.values[idx] = value;
                    return;
                }
            }
            idx = idx + 1;
            if (idx >= cap) {
                idx = 0;
            }
            probed = probed + 1;
        }
        if (firstDeleted >= 0) {
            this.keys[firstDeleted] = key;
            this.values[firstDeleted] = value;
            this.states[firstDeleted] = OCCUPIED;
            this.size = this.size + 1;
            return;
        }
        throw new IllegalStateException("HashMap full");
    }

    public int size() {
        return this.size;
    }

    public boolean isEmpty() {
        return this.size == 0;
    }

    public V put(K key, V value) {
        if (this.states == null) {
            allocTable(8);
        }
        if (this.size + 1 > this.threshold) {
            resize();
        }
        int cap = capacity();
        int h = spreadHash(key.hashCode());
        int idx = indexFor(h, cap);
        int firstDeleted = -1;
        int probed = 0;
        while (probed < cap) {
            int st = this.states[idx];
            if (st == EMPTY) {
                int dest = idx;
                if (firstDeleted >= 0) {
                    dest = firstDeleted;
                }
                this.keys[dest] = key;
                this.values[dest] = value;
                this.states[dest] = OCCUPIED;
                this.size = this.size + 1;
                this.modCount = this.modCount + 1;
                return null;
            }
            if (st == DELETED) {
                if (firstDeleted < 0) {
                    firstDeleted = idx;
                }
            } else {
                if (keysEqual(this.keys[idx], key)) {
                    V old = this.values[idx];
                    this.values[idx] = value;
                    return old;
                }
            }
            idx = idx + 1;
            if (idx >= cap) {
                idx = 0;
            }
            probed = probed + 1;
        }
        throw new IllegalStateException("HashMap full");
    }

    int findSlot(K key) {
        int cap = capacity();
        if (cap == 0) {
            return -1;
        }
        int h = spreadHash(key.hashCode());
        int idx = indexFor(h, cap);
        int probed = 0;
        while (probed < cap) {
            int st = this.states[idx];
            if (st == EMPTY) {
                return -1;
            }
            if (st == OCCUPIED && keysEqual(this.keys[idx], key)) {
                return idx;
            }
            idx = idx + 1;
            if (idx >= cap) {
                idx = 0;
            }
            probed = probed + 1;
        }
        return -1;
    }

    public V get(K key) {
        int slot = findSlot(key);
        if (slot < 0) {
            return null;
        }
        return this.values[slot];
    }

    public boolean containsKey(K key) {
        return findSlot(key) >= 0;
    }

    public V remove(K key) {
        int slot = findSlot(key);
        if (slot < 0) {
            return null;
        }
        V old = this.values[slot];
        this.keys[slot] = null;
        this.values[slot] = null;
        this.states[slot] = DELETED;
        this.size = this.size - 1;
        this.modCount = this.modCount + 1;
        return old;
    }

    public void clear() {
        int cap = capacity();
        int i = 0;
        while (i < cap) {
            this.keys[i] = null;
            this.values[i] = null;
            this.states[i] = EMPTY;
            i = i + 1;
        }
        this.size = 0;
        this.modCount = this.modCount + 1;
    }

    /** Snapshot of occupied keys (iteration support for HashSet). Unordered. */
    public K[] keysArray() {
        K[] out = new K[this.size];
        int n = 0;
        int cap = capacity();
        int i = 0;
        while (i < cap) {
            if (this.states[i] == OCCUPIED) {
                out[n] = this.keys[i];
                n = n + 1;
            }
            i = i + 1;
        }
        return out;
    }

    /**
     * Entry iterator (unordered). Fail-fast on structural modification via modCount.
     * Does not claim concurrent safety.
     */
    public HashMapEntryIterator<K, V> entryIterator() {
        return new HashMapEntryIterator<K, V>(this);
    }
}

class HashMapEntry<K, V> implements Entry<K, V> {
    K key;
    V value;

    HashMapEntry(K key, V value) {
        this.key = key;
        this.value = value;
    }

    public K getKey() {
        return this.key;
    }

    public V getValue() {
        return this.value;
    }
}

class HashMapEntryIterator<K, V> implements Iterator<HashMapEntry<K, V>> {
    HashMap<K, V> map;
    int slot;
    int expectedModCount;
    HashMapEntry<K, V> nextEntry;

    HashMapEntryIterator(HashMap<K, V> map) {
        this.map = map;
        this.slot = 0;
        this.expectedModCount = map.modCount;
        this.nextEntry = null;
        advance();
    }

    void advance() {
        this.nextEntry = null;
        int cap = this.map.capacity();
        while (this.slot < cap) {
            int i = this.slot;
            this.slot = this.slot + 1;
            if (this.map.states[i] == HashMap.OCCUPIED) {
                this.nextEntry = new HashMapEntry<K, V>(this.map.keys[i], this.map.values[i]);
                return;
            }
        }
    }

    public boolean hasNext() {
        return this.nextEntry != null;
    }

    public HashMapEntry<K, V> next() {
        if (this.expectedModCount != this.map.modCount) {
            throw new ConcurrentModificationException("HashMap modified during entry iteration");
        }
        if (this.nextEntry == null) {
            throw new NoSuchElementException("HashMapEntryIterator");
        }
        HashMapEntry<K, V> e = this.nextEntry;
        advance();
        return e;
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
        if (this.map.containsKey(value)) {
            return false;
        }
        this.map.put(value, this.present);
        return true;
    }

    public boolean remove(T value) {
        if (!this.map.containsKey(value)) {
            return false;
        }
        this.map.remove(value);
        return true;
    }

    public void clear() {
        this.map.clear();
    }

    public Iterator<T> iterator() {
        return new ArrayIterator<T>(this.map.keysArray());
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

class Arrays {
    static <T> T[] copyOf(T[] original, int newLength) {
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

    static void fill(Object[] a, Object value) {
        int i = 0;
        while (i < a.length) {
            a[i] = value;
            i = i + 1;
        }
    }

    static boolean arraysEqual(Object[] a, Object[] b) {
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
            if (x == y) {
                i = i + 1;
            } else {
                if (x == null || y == null) {
                    return false;
                }
                if (!x.equals(y)) {
                    return false;
                }
                i = i + 1;
            }
        }
        return true;
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
