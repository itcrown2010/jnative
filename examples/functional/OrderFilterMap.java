/**
 * Collection callbacks: filter / mapValues / forEach on order-like records.
 * Build: java -jar target/jnative.jar build examples/functional/OrderFilterMap.java -o build/OrderFilterMap
 * @author 张晓达
 */
class OrderRow {
    String id;
    String note;
    int totalCents;

    OrderRow(String id, String note, int totalCents) {
        this.id = id;
        this.note = note;
        this.totalCents = totalCents;
    }
}

public class OrderFilterMap {
    public static void main(String[] args) {
        ArrayList<OrderRow> orders = new ArrayList<OrderRow>();
        orders.add(new OrderRow("1", "苹果", 1000));
        orders.add(new OrderRow("2", "香蕉🍌", 500));
        orders.add(new OrderRow("3", "小计", 100));

        ArrayList<OrderRow> big = orders.filter(o -> o.totalCents >= 500);
        System.out.println(big.size());

        ArrayList<Object> ids = orders.mapValues(o -> o.id);
        System.out.println(ids.size());
        System.out.println(((String) ids.get(0)).length());

        Counter sum = new Counter();
        big.forEach(o -> {
            sum.n = sum.n + o.totalCents;
        });
        System.gc();
        System.out.println(sum.n);

        // Independent result: mutating filter output does not change source
        big.clear();
        System.out.println(orders.size());
    }
}

class Counter {
    int n;
}
