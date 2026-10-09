/**
 * Demo: final locals, definite assignment, and array enhanced-for.
 *
 * Unsupported in this profile (hard errors):
 * - final fields
 * - Iterable-based enhanced-for
 */
public class FinalDaForeachDemo {
    public static void main(String[] args) {
        final int seed = 10;
        int total;
        if (args.length > 0) {
            total = seed;
        } else {
            total = seed + 5;
        }

        int[] numbers = new int[4];
        numbers[0] = 1;
        numbers[1] = 2;
        numbers[2] = 3;
        numbers[3] = 4;
        for (int value : numbers) {
            total = total + value;
        }
        System.out.println(total);
    }
}
