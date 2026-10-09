package com.demo.catalog;

/** Catalog product (distinct package from order.OrderLine naming).
 * @author 张晓达
 */
public class Product {
    public String sku;
    public String name;
    public int unitCents;

    public Product(String sku, String name, int unitCents) {
        this.sku = sku;
        this.name = name;
        this.unitCents = unitCents;
    }
}
