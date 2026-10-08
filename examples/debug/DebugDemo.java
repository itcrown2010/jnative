class DebugDemo {
    static int add(int a, int b) {
        int result = a + b;
        return result;
    }

    public static void main(String[] args) {
        int x = 10;
        int y = 20;
        int sum = add(x, y);
        System.out.println(sum);
    }
}
