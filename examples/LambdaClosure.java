public class LambdaClosure {
    static IntUnaryFunction create(int base) {
        return x -> x + base;
    }

    public static void main(String[] args) {
        IntUnaryFunction f = create(100);
        System.gc();
        System.out.println(f.apply(23));
        Thread t = new Thread(() -> System.out.println(f.apply(23)));
        t.start();
        t.join();
    }
}
