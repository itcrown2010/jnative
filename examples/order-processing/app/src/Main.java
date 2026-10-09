package com.demo.app;

import com.demo.order.OrderPipeline;

/**
 * Capability integration entry: multipackage + third-party moneykit + collections + enum/switch
 * + Unicode + TWR file + exceptions/finally.
 *
 * Args:
 *   (none)|ok   — normal path
 *   bad         — illegal transition error path
 *   stress [N]  — bounded GC/resource stress (default N=200)
 * @author 张晓达
 */
public class Main {
    public static void main(String[] args) {
        String mode = "ok";
        int stressN = 200;
        if (args != null && args.length > 0) {
            mode = args[0];
            // Fixed stress sizes only (no Integer.parseInt in language surface yet).
            if (mode.equals("stress50")) {
                mode = "stress";
                stressN = 50;
            } else if (mode.equals("stress500")) {
                mode = "stress";
                stressN = 500;
            } else if (mode.equals("stress")) {
                stressN = 200;
            }
        }

        OrderPipeline pipe = new OrderPipeline();
        if (mode.equals("bad")) {
            pipe.runBad();
            return;
        }
        if (mode.equals("stress")) {
            // Prefix under project build/ (created by jnative build); no nested mkdir.
            String dir = "build/stress";
            int cs = pipe.runStress(stressN, dir);
            System.out.println(cs);
            System.out.println(pipe.store.size());
            return;
        }

        String summary = "build/order-summary.txt";
        pipe.runOk(summary);
    }
}
