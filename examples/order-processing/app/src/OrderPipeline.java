package com.demo.order;

import com.acme.money.MoneyCents;
import com.demo.catalog.Product;

/**
 * Load → validate → aggregate → query. Fixed-seed friendly for tests.
 * @author 张晓达
 */
public class OrderPipeline {
    public OrderStore store;
    public int cleanupFlag;
    public int lastError;

    public OrderPipeline() {
        this.store = new OrderStore();
        this.cleanupFlag = 0;
        this.lastError = 0;
    }

    public Order buildSampleOrder() {
        Product apple = new Product("SKU-A", "苹果", 1000);
        Product banana = new Product("SKU-B", "香蕉🍌", 750);
        Order order = new Order("1001", "订单#1001 🍎");
        order.addLine(new OrderLine(apple.sku, 2, apple.unitCents));
        order.addLine(new OrderLine(banana.sku, 2, banana.unitCents));
        return order;
    }

    public void validate(Order order) {
        if (order.lines.size() == 0) {
            throw new OrderException("empty order");
        }
        for (OrderLine line : order.lines) {
            if (line.qty <= 0) {
                throw new OrderException("bad qty");
            }
        }
        if (order.totalCents <= 0) {
            throw new OrderException("bad total");
        }
    }

    /** Normal path. Prints stable numeric markers for Java/O0/opt compare. */
    public void runOk(String summaryPath) {
        this.lastError = 0;
        Order order = null;
        try {
            order = buildSampleOrder();
            validate(order);
            OrderTransitions.apply(order, OrderStatus.APPROVED);
            OrderTransitions.apply(order, OrderStatus.POSTED);
            this.store.put(order);

            Order found = this.store.get("1001");
            int prefer = MoneyCents.prefer(3);
            Integer boxed = MoneyCents.identity(Integer.valueOf(7));
            int bytes = this.store.writeSummary(summaryPath);
            String back = this.store.readSummaryTwR(summaryPath);
            int hasEmoji = 0;
            if (back.indexOf("🍎") >= 0) {
                hasEmoji = 1;
            }

            System.out.println(1);
            System.out.println(found.totalCents);
            System.out.println(OrderTransitions.statusCode(found.status));
            System.out.println(this.store.size());
            System.out.println(prefer);
            System.out.println(boxed.intValue());
            System.out.println(bytes);
            System.out.println(hasEmoji);
            System.out.println(found.note.length());
        } catch (OrderException e) {
            this.lastError = 1;
            System.out.println(0);
            System.out.println(e.getMessage().length() > 0 ? 1 : 0);
        } finally {
            // Allocate in finally — prior results must remain valid.
            Integer marker = Integer.valueOf(42);
            this.cleanupFlag = marker.intValue();
            System.out.println(this.cleanupFlag);
        }
    }

    /** Error path: illegal transition; finally still runs. */
    public void runBad() {
        this.lastError = 0;
        try {
            Order order = buildSampleOrder();
            validate(order);
            OrderTransitions.apply(order, OrderStatus.POSTED);
            System.out.println(9);
        } catch (OrderException e) {
            this.lastError = 1;
            System.out.println(0);
            System.out.println(1);
        } finally {
            this.cleanupFlag = 42;
            System.out.println(this.cleanupFlag);
        }
    }

    /**
     * Bounded stress: create/query/remove, GC, open/close files.
     * seed controls iteration count (fixed for reproducibility).
     */
    public int runStress(int seed, String dir) {
        int n = seed;
        if (n < 1) {
            n = 1;
        }
        if (n > 2000) {
            n = 2000;
        }
        ResourceTracker.reset();
        int checksum = 0;
        int i = 0;
        while (i < n) {
            Order o = new Order(String.valueOf(i), "压测");
            o.addLine(new OrderLine("S", 1, 100 + (i % 17)));
            this.store.put(o);
            Order got = this.store.get(String.valueOf(i));
            checksum = checksum + got.totalCents;
            // Flat files under existing build/ (no mkdir dependency).
            String path = dir + "-s-" + String.valueOf(i % 8) + ".txt";
            this.store.writeSummary(path);
            String again = File.readString(path);
            checksum = checksum + again.length();
            if ((i % 32) == 31) {
                System.gc();
            }
            if ((i % 5) == 4) {
                this.store.remove(String.valueOf(i));
            }
            i = i + 1;
        }
        int opens = ResourceTracker.files();
        // File.readString/writeString are builtins (not always tracked); TWR path uses tracker.
        checksum = checksum + opens;
        checksum = checksum + this.store.size();
        return checksum;
    }
}
