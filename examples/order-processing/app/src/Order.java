package com.demo.order;

import com.acme.money.MoneyCents;

public class Order {
    public String id;
    public String note;
    public OrderStatus status;
    public ArrayList<OrderLine> lines;
    public int totalCents;

    public Order(String id, String note) {
        this.id = id;
        this.note = note;
        this.status = OrderStatus.DRAFT;
        this.lines = new ArrayList<OrderLine>();
        this.totalCents = 0;
    }

    public void addLine(OrderLine line) {
        this.lines.add(line);
        recomputeTotal();
    }

    public void recomputeTotal() {
        int sum = 0;
        for (OrderLine line : this.lines) {
            int lineTotal = MoneyCents.mulQty(line.unitCents, line.qty);
            sum = MoneyCents.add(sum, lineTotal);
        }
        this.totalCents = sum;
    }
}
