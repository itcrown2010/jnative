/**
 * @author 张晓达
 */
class Counter {
    int value;

    void increment() {
        this.value = this.value + 1;
    }

    int get() {
        return this.value;
    }
}

public class CounterThis {
    public static void main(String[] args) {
        Counter c = new Counter();
        c.increment();
        c.increment();
        c.increment();
        System.out.println(c.get());
    }
}
