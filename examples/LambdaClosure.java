interface IntUnary {
    int apply(int value);
}

/** Escape + capture env + GC smoke (see docs/jnative-closures.md).
 * @author 张晓达
 */
public class LambdaClosure {
    static IntUnary addBy(int base) {
        return value -> value + base;
    }

    static IntUnary nest(int a) {
        int b = 10;
        return x -> {
            IntUnary inner = y -> y + a + b;
            return inner.apply(x);
        };
    }

    public static void main(String[] args) {
        IntUnary f = addBy(100);
        System.gc();
        System.out.println(f.apply(23));
        IntUnary g = nest(5);
        System.gc();
        System.out.println(g.apply(1));
        Thread t = new Thread(() -> System.out.println(f.apply(23)));
        t.start();
        t.join();
    }
}
