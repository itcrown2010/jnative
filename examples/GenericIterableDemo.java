/**
 * Demo: generics type safety + ArrayList Iterable enhanced-for.
 *
 * Strategy: reference type args erase; primitive args specialize (JNative extension).
 * Raw types and wildcards (beyond parsing) are out of scope.
 */
class Item {
    public int quantity;
    public Item(int quantity) {
        this.quantity = quantity;
    }
}

class Box<T> {
    T value;
    Box(T value) {
        this.value = value;
    }
    void set(T value) {
        this.value = value;
    }
    T get() {
        return value;
    }
}

public class GenericIterableDemo {
    public static void main(String[] args) {
        Box<String> box = new Box<String>("ok");
        System.out.println(box.get());

        ArrayList<Item> items = new ArrayList<Item>();
        items.add(new Item(2));
        items.add(new Item(5));
        int total = 0;
        for (Item item : items) {
            total = total + item.quantity;
        }
        System.out.println(total);
    }
}
