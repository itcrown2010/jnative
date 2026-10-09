// jnative.browser — order list demo for wasm32-browser
// Business logic runs in WASM; JS only loads the module and hosts DOM nodes in index.html.

class OrderListApp {
    static ArrayList<OrderItem> items;
    static int nextId;

    public static void main(String[] args) {
        items = new ArrayList<OrderItem>();
        nextId = 1;
        wireUi();
        render();
    }

    static void wireUi() {
        Document doc = Browser.document();
        Element addBtn = doc.getElementById("addBtn");
        Element clearBtn = doc.getElementById("clearBtn");
        if (addBtn != null) {
            addBtn.addEventListener("click", (BrowserEvent e) -> {
                onAddClicked();
            });
        }
        if (clearBtn != null) {
            clearBtn.addEventListener("click", (BrowserEvent e) -> {
                onClearClicked();
            });
        }
    }

    static void onAddClicked() {
        Document doc = Browser.document();
        Element nameEl = doc.getElementById("itemName");
        Element qtyEl = doc.getElementById("itemQty");
        Element priceEl = doc.getElementById("itemPrice");
        String name = "商品";
        int qty = 1;
        double price = 1.0;
        if (nameEl != null) {
            String n = nameEl.asInput().value();
            if (n != null && n.length() > 0) {
                name = n;
            }
        }
        if (qtyEl != null) {
            String q = qtyEl.asInput().value();
            qty = parsePositiveInt(q, 1);
        }
        if (priceEl != null) {
            String p = priceEl.asInput().value();
            price = parsePositiveDouble(p, 1.0);
        }
        items.add(new OrderItem(name, qty, price));
        nextId = nextId + 1;
        // One collection op with lambda (forEach)
        IntBox lines = new IntBox();
        items.forEach((OrderItem it) -> {
            lines.value = lines.value + 1;
        });
        render();
        Browser.console().log("已添加，行数=" + Integer.toString(lines.value));
    }

    static void onClearClicked() {
        if (items.size() > 0) {
            items.removeAt(items.size() - 1);
        }
        render();
    }

    static void render() {
        Document doc = Browser.document();
        Element list = doc.getElementById("orderList");
        Element totalQtyEl = doc.getElementById("totalQty");
        Element totalPriceEl = doc.getElementById("totalPrice");
        String body = "";
        int totalQty = 0;
        double totalPrice = 0.0;
        for (OrderItem it : items) {
            totalQty = totalQty + it.quantity;
            totalPrice = totalPrice + it.lineTotal();
            body = body + it.name + " × " + Integer.toString(it.quantity)
                    + " @ " + formatMoney(it.unitPrice)
                    + " = " + formatMoney(it.lineTotal()) + "\n";
        }
        if (list != null) {
            list.setText(body);
        }
        if (totalQtyEl != null) {
            totalQtyEl.setText(Integer.toString(totalQty));
        }
        if (totalPriceEl != null) {
            totalPriceEl.setText(formatMoney(totalPrice));
        }
    }

    /** Format with two decimal places via integer cents (avoids JS Number for money). */
    static String formatMoney(double v) {
        int cents = (int) (v * 100.0 + 0.5);
        int yuan = cents / 100;
        int rem = cents - yuan * 100;
        if (rem < 0) {
            rem = 0 - rem;
        }
        String r = Integer.toString(rem);
        if (rem < 10) {
            r = "0" + r;
        }
        return Integer.toString(yuan) + "." + r;
    }

    static int parsePositiveInt(String s, int fallback) {
        if (s == null || s.length() == 0) {
            return fallback;
        }
        int n = 0;
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return fallback;
            }
            n = n * 10 + (c - '0');
            i = i + 1;
        }
        if (n <= 0) {
            return fallback;
        }
        return n;
    }

    static double parsePositiveDouble(String s, double fallback) {
        if (s == null || s.length() == 0) {
            return fallback;
        }
        // ASCII only; host may pre-validate
        int i = 0;
        int whole = 0;
        while (i < s.length() && s.charAt(i) != '.') {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return fallback;
            }
            whole = whole * 10 + (c - '0');
            i = i + 1;
        }
        double frac = 0.0;
        double place = 0.1;
        if (i < s.length() && s.charAt(i) == '.') {
            i = i + 1;
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c < '0' || c > '9') {
                    return fallback;
                }
                frac = frac + (double) (c - '0') * place;
                place = place * 0.1;
                i = i + 1;
            }
        }
        double v = (double) whole + frac;
        if (v <= 0.0) {
            return fallback;
        }
        return v;
    }
}

class IntBox {
    int value;

    IntBox() {
        this.value = 0;
    }
}
