// JNative Collections Framework (0.16) — library code, NOT compiler intrinsics.
// Namespace: jnative.collections / jnative.function (flattened; no package system yet).
//
// Equality: reference collections use Object.equals / Object.hashCode.
// Default Object.equals is identity; Object.hashCode is a stable identity hash
// (survives moving GC2). String overrides both.
// ArrayList.contains / indexOf use == (identity for refs, value eq for primitives).
// Null keys are unsupported in HashMap 0.16.

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
    T removeAt(int index);
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
        return oldCapacity + (oldCapacity / 2);
    }

    void ensureCapacity(int minCapacity) {
        int cap = 0;
        if (this.storage != null) {
            cap = this.storage.length;
        }
        if (minCapacity <= cap) {
            return;
        }
        int newCap = calculateNewCapacity(cap);
        if (newCap < minCapacity) {
            newCap = minCapacity;
        }
        T[] neu = new T[newCap];
        int i = 0;
        while (i < this.size) {
            neu[i] = this.storage[i];
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
            if (this.storage[i] == value) {
                return i;
            }
            i = i + 1;
        }
        return -1;
    }

    public boolean contains(T value) {
        return indexOf(value) >= 0;
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
}

class PresentMarker {
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
        // 0.16: key-set iteration deferred; fail fast rather than return a partial view.
        if (true) {
            throw new IllegalStateException("HashSet.iterator not implemented in 0.16");
        }
        return null;
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

    public T peekFirst() {
        if (this.size == 0) {
            return null;
        }
        return this.storage[this.head];
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
