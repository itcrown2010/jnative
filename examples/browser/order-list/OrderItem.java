/** One line item in the browser order list demo.
 * @author 张晓达
 */
class OrderItem {
    String name;
    int quantity;
    double unitPrice;

    OrderItem(String name, int quantity, double unitPrice) {
        this.name = name;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    double lineTotal() {
        return (double) this.quantity * this.unitPrice;
    }
}
