/**
 * Standalone float/double numerical example (means / variance).
 * For approximate statistics and scientific smoke only — not money.
 * ERP / finance amounts must use integer minor units or Decimal, never binary float.
 * @author 张晓达
 */
public class NumericStats {
    public static void main(String[] args) {
        double[] xs = new double[5];
        xs[0] = 1.5;
        xs[1] = 2.0;
        xs[2] = 2.5;
        xs[3] = 3.0;
        xs[4] = 10.0;

        double mean = mean(xs);
        double var = variance(xs, mean);
        System.out.println(mean);
        System.out.println(var);

        float sumf = 0.0f;
        for (int i = 0; i < xs.length; i = i + 1) {
            sumf = sumf + (float) xs[i];
        }
        // Print scaled int — float→string uses %g (not Double.toString parity).
        System.out.println((int) (sumf * 10.0f));

        // Special values stay IEEE (Inf / NaN), not integer traps
        double inf = 1.0 / 0.0;
        double nan = 0.0 / 0.0;
        if (inf > 0.0) {
            System.out.println(1);
        } else {
            System.out.println(0);
        }
        if (nan != nan) {
            System.out.println(1);
        } else {
            System.out.println(0);
        }
    }

    static double mean(double[] xs) {
        double s = 0.0;
        for (int i = 0; i < xs.length; i = i + 1) {
            s = s + xs[i];
        }
        return s / (double) xs.length;
    }

    static double variance(double[] xs, double mean) {
        double s = 0.0;
        for (int i = 0; i < xs.length; i = i + 1) {
            double d = xs[i] - mean;
            s = s + d * d;
        }
        return s / (double) xs.length;
    }
}
