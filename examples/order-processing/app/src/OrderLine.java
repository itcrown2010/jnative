package com.demo.order;

/** Line item — same simple name "Item" avoided; lives in order package.
 * @author 张晓达
 */
public class OrderLine {
    public String sku;
    public int qty;
    public int unitCents;

    public OrderLine(String sku, int qty, int unitCents) {
        this.sku = sku;
        this.qty = qty;
        this.unitCents = unitCents;
    }
}
