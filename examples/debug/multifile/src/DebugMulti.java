/**
 * Cross-file debug demo: main in this file, add() in MathUtil.java.
 * Uses System.out (stdlib) — breakpoints must still land on user lines.
 * @author 张晓达
 */
class DebugMulti {
    public static void main(String[] args) {
        int x = 10;
        int y = 20;
        boolean ready = true;
        long xl = 10L;
        long yl = 20L;
        int sum = MathUtil.add(x, y);
        long sumL = MathUtil.addLong(xl, yl);
        if (ready) {
            System.out.println(sum);
            System.out.println(sumL);
        }
    }
}
