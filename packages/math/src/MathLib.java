class MathLib {
    static int add(int a, int b) {
        return a + b;
    }
    static int saturate(int v) {
        if (v < 0) {
            return 0;
        }
        return v;
    }
}
