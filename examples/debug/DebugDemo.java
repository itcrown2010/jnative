/**
 * @author 张晓达
 */
class DebugDemo {
    static int add(int a, int b) {
        int result = a + b;
        return result;
    }

    public static void main(String[] args) {
        int x = 10;
        int y = 20;
        int sum = add(x, y);
        double surplus=Math.sqrt(9);
        System.out.println(sum);
        System.out.println("hello");
        System.out.println(surplus);
    }
}
