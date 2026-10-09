/**
 * Independent order-status demo: enum + integer/enum switch for legal transitions.
 * Not an ERP rewrite — just language-level state machine illustration.
 *
 * Persistence note: do not store {@code ordinal()} as a DB key; use an explicit code.
 * @author 张晓达
 */
enum OrderStatus {
    DRAFT,
    APPROVED,
    POSTED,
    CANCELLED
}

public class OrderStatusDemo {
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

    public static int transitionCode(OrderStatus from, OrderStatus to) {
        if (!canTransition(from, to)) {
            return -1;
        }
        // Explicit stable codes (not ordinal) for illustration.
        int code = 0;
        switch (to) {
            case APPROVED:
                code = 100;
                break;
            case POSTED:
                code = 200;
                break;
            case CANCELLED:
                code = 900;
                break;
            default:
                code = 0;
        }
        return code;
    }

    public static void main(String[] args) {
        OrderStatus s = OrderStatus.DRAFT;
        System.out.println(s.name());
        System.out.println(canTransition(s, OrderStatus.APPROVED) ? 1 : 0);
        System.out.println(transitionCode(s, OrderStatus.APPROVED));
        System.out.println(canTransition(OrderStatus.POSTED, OrderStatus.DRAFT) ? 1 : 0);

        OrderStatus[] all = OrderStatus.values();
        System.out.println(all.length);
        for (int i = 0; i < all.length; i = i + 1) {
            System.out.println(all[i].ordinal());
        }
    }
}
