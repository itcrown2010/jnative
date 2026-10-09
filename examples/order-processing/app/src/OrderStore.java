package com.demo.order;

/**
 * In-memory index + Unicode file snapshot.
 * ArrayList&lt;Order&gt; for order, HashMap&lt;String, Order&gt; for lookup.
 */
public class OrderStore {
    public ArrayList<Order> orders;
    public HashMap<String, Order> byId;

    public OrderStore() {
        this.orders = new ArrayList<Order>();
        this.byId = new HashMap<String, Order>();
    }

    public void put(Order order) {
        Order prev = this.byId.get(order.id);
        if (prev == null) {
            this.orders.add(order);
        }
        this.byId.put(order.id, order);
    }

    public Order get(String id) {
        return this.byId.get(id);
    }

    /** Remove from both list and map (keeps indices consistent). */
    public void remove(String id) {
        Order prev = this.byId.remove(id);
        if (prev == null) {
            return;
        }
        int i = 0;
        while (i < this.orders.size()) {
            Order o = this.orders.get(i);
            if (o.id.equals(id)) {
                this.orders.removeAt(i);
                return;
            }
            i = i + 1;
        }
    }

    public int size() {
        return this.orders.size();
    }

    public int sumTotals() {
        int sum = 0;
        for (Order o : this.orders) {
            sum = sum + o.totalCents;
        }
        return sum;
    }

    /** Write Unicode summary; returns byte length of written text. */
    public int writeSummary(String path) {
        StringBuilder sb = new StringBuilder();
        for (Order o : this.orders) {
            sb.append(o.id);
            sb.append("|");
            sb.append(o.note);
            sb.append("|");
            sb.append(String.valueOf(o.totalCents));
            sb.append("|");
            sb.append(o.status.name());
            sb.append("\n");
        }
        String text = sb.toString();
        File.writeString(path, text);
        return text.getBytes().length;
    }

    /** Read summary via try-with-resources FileInputStream (UTF-8 bytes → String). */
    public String readSummaryTwR(String path) {
        byte[] buf = new byte[4096];
        int n = 0;
        try (FileInputStream in = new FileInputStream(path)) {
            int r = in.read(buf, 0, buf.length);
            if (r > 0) {
                n = r;
            }
        }
        if (n <= 0) {
            return "";
        }
        byte[] exact = new byte[n];
        int i = 0;
        while (i < n) {
            exact[i] = buf[i];
            i = i + 1;
        }
        return String.fromBytes(exact);
    }
}
