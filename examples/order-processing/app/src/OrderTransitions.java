package com.demo.order;

/** Enum + switch state machine (explicit codes, not ordinal persistence).
 * @author 张晓达
 */
public class OrderTransitions {
    public static boolean canTransition(OrderStatus from, OrderStatus to) {
        switch (from) {
            case DRAFT:
                return to == OrderStatus.APPROVED || to == OrderStatus.CANCELLED;
            case APPROVED:
                return to == OrderStatus.POSTED || to == OrderStatus.CANCELLED;
            case POSTED:
                return false;
            case CANCELLED:
                return false;
            default:
                return false;
        }
    }

    public static int statusCode(OrderStatus s) {
        switch (s) {
            case DRAFT:
                return 0;
            case APPROVED:
                return 100;
            case POSTED:
                return 200;
            case CANCELLED:
                return 900;
            default:
                return -1;
        }
    }

    public static void apply(Order order, OrderStatus to) {
        if (!canTransition(order.status, to)) {
            throw new OrderException("illegal transition "
                    + order.status.name() + " -> " + to.name());
        }
        order.status = to;
    }
}
