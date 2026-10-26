/**
 * SAP B1 style master data: descriptors for generic CRUD, company defaults and old-data upgrade,
 * price-list / tax-code resolution, exchange rates and credit-limit checks.
 * @author 张晓达
 */
class B1Entity {
    String name;
    String table;
    String orderBy;
    String label;
    int stamp;
    ArrayList<Object> cols;
    ArrayList<Object> types;
    ArrayList<Object> keys;
    ArrayList<Object> filters;
    ArrayList<Object> search;
    ArrayList<Object> fkCols;
    ArrayList<Object> fkTables;

    B1Entity(String name, String table, String label) {
        this.name = name;
        this.table = table;
        this.label = label;
        this.orderBy = "id";
        this.stamp = 1;
        this.cols = new ArrayList<Object>();
        this.types = new ArrayList<Object>();
        this.keys = new ArrayList<Object>();
        this.filters = new ArrayList<Object>();
        this.search = new ArrayList<Object>();
        this.fkCols = new ArrayList<Object>();
        this.fkTables = new ArrayList<Object>();
    }

    void s(String c) {
        cols.add(c);
        types.add("s");
    }

    void i(String c) {
        cols.add(c);
        types.add("i");
    }

    void fk(String c, String t) {
        fkCols.add(c);
        fkTables.add(t);
    }

    int isInt(String c) {
        int k = 0;
        while (k < cols.size()) {
            if (((String) cols.get(k)).equals(c)) {
                if (((String) types.get(k)).equals("i")) {
                    return 1;
                }
                return 0;
            }
            k = k + 1;
        }
        return 0;
    }

    int hasCol(String c) {
        int k = 0;
        while (k < cols.size()) {
            if (((String) cols.get(k)).equals(c)) {
                return 1;
            }
            k = k + 1;
        }
        return 0;
    }
}

class B1Price {
    long priceCents;
    String source;
    long discountBps;
    long taxCodeId;
    String taxCode;
    long taxBps;
    String uom;
    long isInventory;
    String currency;
}

class ErpB1Master {
    static B1Entity entity(String n) {
        if (n == null) {
            return null;
        }
        if (n.equals("bpGroups")) {
            B1Entity e = new B1Entity(n, "bp_groups", "BP Group");
            e.s("code");
            e.s("name");
            e.s("kind");
            e.i("active");
            e.search.add("code");
            e.search.add("name");
            e.filters.add("kind");
            e.filters.add("active");
            return e;
        }
        if (n.equals("paymentTerms")) {
            B1Entity e = new B1Entity(n, "payment_terms", "Payment Terms");
            e.s("code");
            e.s("name");
            e.i("net_days");
            e.i("discount_days");
            e.i("discount_bps");
            e.i("active");
            e.search.add("code");
            e.search.add("name");
            e.filters.add("active");
            return e;
        }
        if (n.equals("taxCodes")) {
            B1Entity e = new B1Entity(n, "tax_codes", "Tax Code");
            e.s("code");
            e.s("name");
            e.i("rate_bps");
            e.s("direction");
            e.i("active");
            e.search.add("code");
            e.search.add("name");
            e.filters.add("direction");
            e.filters.add("active");
            return e;
        }
        if (n.equals("currencies")) {
            B1Entity e = new B1Entity(n, "currencies", "Currency");
            e.s("code");
            e.s("name");
            e.s("symbol");
            e.i("decimals");
            e.i("is_local");
            e.i("active");
            e.search.add("code");
            e.search.add("name");
            e.filters.add("active");
            return e;
        }
        if (n.equals("exchangeRates")) {
            B1Entity e = new B1Entity(n, "exchange_rates", "Exchange Rate");
            e.s("currency_code");
            e.i("rate_date");
            e.i("rate_micro");
            e.keys.add("currency_code");
            e.keys.add("rate_date");
            e.filters.add("currency_code");
            e.orderBy = "rate_date DESC, id DESC";
            return e;
        }
        if (n.equals("priceLists")) {
            B1Entity e = new B1Entity(n, "price_lists", "Price List");
            e.s("code");
            e.s("name");
            e.s("currency_code");
            e.i("base_list_id");
            e.i("factor_bps");
            e.i("is_default");
            e.i("active");
            e.search.add("code");
            e.search.add("name");
            e.filters.add("active");
            return e;
        }
        if (n.equals("priceListItems")) {
            B1Entity e = new B1Entity(n, "price_list_items", "Price List Item");
            e.stamp = 0;
            e.i("price_list_id");
            e.i("item_id");
            e.i("price_cents");
            e.keys.add("price_list_id");
            e.keys.add("item_id");
            e.filters.add("price_list_id");
            e.filters.add("item_id");
            e.fk("price_list_id", "price_lists");
            e.fk("item_id", "items");
            return e;
        }
        if (n.equals("itemGroups")) {
            B1Entity e = new B1Entity(n, "item_groups", "Item Group");
            e.s("code");
            e.s("name");
            e.s("valuation_method");
            e.i("active");
            e.search.add("code");
            e.search.add("name");
            e.filters.add("active");
            return e;
        }
        if (n.equals("uomGroups")) {
            B1Entity e = new B1Entity(n, "uom_groups", "UoM Group");
            e.s("code");
            e.s("name");
            e.s("base_uom");
            e.i("active");
            e.search.add("code");
            e.search.add("name");
            e.filters.add("active");
            return e;
        }
        if (n.equals("uomGroupItems")) {
            B1Entity e = new B1Entity(n, "uom_group_items", "UoM Conversion");
            e.stamp = 0;
            e.i("uom_group_id");
            e.s("uom_code");
            e.i("base_qty");
            e.keys.add("uom_group_id");
            e.keys.add("uom_code");
            e.filters.add("uom_group_id");
            e.fk("uom_group_id", "uom_groups");
            return e;
        }
        if (n.equals("bpAddresses")) {
            B1Entity e = new B1Entity(n, "bp_addresses", "BP Address");
            e.i("partner_id");
            e.s("addr_type");
            e.s("addr_name");
            e.s("street");
            e.s("city");
            e.s("state");
            e.s("zip");
            e.s("country");
            e.i("is_default");
            e.filters.add("partner_id");
            e.fk("partner_id", "business_partners");
            return e;
        }
        if (n.equals("bpContacts")) {
            B1Entity e = new B1Entity(n, "bp_contact_persons", "BP Contact");
            e.i("partner_id");
            e.s("name");
            e.s("title");
            e.s("phone");
            e.s("mobile");
            e.s("email");
            e.i("is_default");
            e.filters.add("partner_id");
            e.fk("partner_id", "business_partners");
            return e;
        }
        if (n.equals("partners")) {
            B1Entity e = new B1Entity(n, "business_partners", "Business Partner");
            e.s("code");
            e.s("name");
            e.s("kind");
            e.i("group_id");
            e.i("payment_terms_id");
            e.i("price_list_id");
            e.s("currency");
            e.s("tax_id");
            e.s("phone");
            e.s("email");
            e.s("website");
            e.i("credit_limit_cents");
            e.i("commitment_limit_cents");
            e.i("discount_bps");
            e.s("status");
            e.i("sales_employee_id");
            e.i("default_tax_code_id");
            e.s("bank_name");
            e.s("bank_account");
            e.s("industry");
            e.s("remarks");
            e.search.add("code");
            e.search.add("name");
            e.search.add("tax_id");
            e.filters.add("kind");
            e.filters.add("group_id");
            e.filters.add("status");
            e.fk("group_id", "bp_groups");
            e.fk("payment_terms_id", "payment_terms");
            e.fk("price_list_id", "price_lists");
            e.fk("default_tax_code_id", "tax_codes");
            return e;
        }
        if (n.equals("items")) {
            B1Entity e = new B1Entity(n, "items", "Item");
            e.s("sku");
            e.s("name");
            e.i("unit_price_cents");
            e.s("category_code");
            e.s("uom");
            e.i("item_group_id");
            e.s("item_type");
            e.i("is_inventory");
            e.i("is_sales");
            e.i("is_purchase");
            e.s("barcode");
            e.s("manage_method");
            e.i("uom_group_id");
            e.s("sales_uom");
            e.s("purchase_uom");
            e.i("sales_tax_code_id");
            e.i("purchase_tax_code_id");
            e.s("valuation_method");
            e.i("default_warehouse_id");
            e.i("preferred_vendor_id");
            e.i("min_qty");
            e.i("max_qty");
            e.i("reorder_qty");
            e.i("lead_time_days");
            e.i("weight_g");
            e.s("status");
            e.s("remarks");
            e.search.add("sku");
            e.search.add("name");
            e.search.add("barcode");
            e.filters.add("item_group_id");
            e.filters.add("item_type");
            e.filters.add("status");
            e.fk("item_group_id", "item_groups");
            e.fk("uom_group_id", "uom_groups");
            e.fk("sales_tax_code_id", "tax_codes");
            e.fk("purchase_tax_code_id", "tax_codes");
            e.fk("default_warehouse_id", "warehouses");
            e.fk("preferred_vendor_id", "business_partners");
            return e;
        }
        return null;
    }

    static String codeCol(B1Entity e) {
        if (e.table.equals("items")) {
            return "sku";
        }
        if (e.hasCol("code") != 0) {
            return "code";
        }
        return "";
    }

    static String spec(B1Entity e) {
        String out = "id:i";
        int i = 0;
        while (i < e.cols.size()) {
            out = out + "|" + (String) e.cols.get(i) + ":" + (String) e.types.get(i);
            i = i + 1;
        }
        return out;
    }

    static String selectList(B1Entity e) {
        String out = "id";
        int i = 0;
        while (i < e.cols.size()) {
            out = out + ", " + (String) e.cols.get(i);
            i = i + 1;
        }
        return out;
    }

    static String list(long companyId, B1Entity e, WebContext ctx) {
        ArrayList<Object> p = ErpB1Util.params();
        p.add(ErpB1Util.lng(companyId));
        String sql = "SELECT " + selectList(e) + " FROM " + e.table + " WHERE company_id=?";
        int i = 0;
        while (i < e.filters.size()) {
            String f = (String) e.filters.get(i);
            String v = ctx.query(ErpB1Util.camel(f));
            if (v != null && v.length() > 0) {
                if (e.table.equals("business_partners") && f.equals("kind")
                        && (v.equals("CUSTOMER") || v.equals("SUPPLIER"))) {
                    sql = sql + " AND (kind=? OR kind='BOTH')";
                } else {
                    sql = sql + " AND " + f + "=?";
                }
                p.add(v);
            }
            i = i + 1;
        }
        String q = ctx.query("q");
        if (q != null && q.length() > 0 && e.search.size() > 0) {
            String like = "%" + ErpB1Util.safeLike(q) + "%";
            sql = sql + " AND (";
            int k = 0;
            while (k < e.search.size()) {
                if (k > 0) {
                    sql = sql + " OR ";
                }
                sql = sql + (String) e.search.get(k) + " LIKE ?";
                p.add(like);
                k = k + 1;
            }
            sql = sql + ")";
        }
        long limit = ErpNum.parseLong(ctx.query("limit"));
        if (limit <= 0L || limit > 2000L) {
            limit = 500L;
        }
        long offset = ErpNum.parseLong(ctx.query("offset"));
        if (offset < 0L) {
            offset = 0L;
        }
        sql = sql + " ORDER BY " + e.orderBy + " LIMIT " + ErpB1Util.lng(limit) + " OFFSET " + ErpB1Util.lng(offset);
        /* Keep every intermediate in a named local so it stays rooted across allocations (parallel GC safety). */
        String sp = spec(e);
        DbQueryResult qr = ErpDb.query(sql, p);
        return ErpB1Util.rowsJsonSpec(qr, sp);
    }

    static String get(long companyId, B1Entity e, long id) {
        ArrayList<Object> p = ErpB1Util.params();
        p.add(ErpB1Util.lng(id));
        p.add(ErpB1Util.lng(companyId));
        DbQueryResult r = ErpDb.query("SELECT " + selectList(e) + " FROM " + e.table + " WHERE id=? AND company_id=?", p);
        if (r.rows.size() == 0) {
            return null;
        }
        return ErpB1Util.rowJsonSpec(r.rows.get(0), spec(e));
    }

    static int rowExists(long companyId, String table, long id) {
        ArrayList<Object> p = ErpB1Util.params();
        p.add(ErpB1Util.lng(id));
        p.add(ErpB1Util.lng(companyId));
        Object v = ErpDb.queryOne("SELECT id FROM " + table + " WHERE id=? AND company_id=?", p, new LongMapper());
        if (v == null) {
            return 0;
        }
        return 1;
    }

    static String validate(long companyId, B1Entity e, JsonObject body, int create, long selfId) {
        String codeCol = codeCol(e);
        if (create != 0 && codeCol.length() > 0) {
            String c = ErpB1Util.text(body, ErpB1Util.camel(codeCol), "").trim();
            if (c.length() == 0) {
                return "code required";
            }
            if (c.length() > 40) {
                return "code too long";
            }
        }
        if (create != 0 && e.hasCol("name") != 0 && ErpB1Util.text(body, "name", "").trim().length() == 0) {
            return "name required";
        }
        int k = 0;
        while (k < e.fkCols.size()) {
            String fc = (String) e.fkCols.get(k);
            String ft = (String) e.fkTables.get(k);
            String key = ErpB1Util.camel(fc);
            if (ErpB1Util.has(body, key) != 0) {
                long v = ErpNum.parseLong(ErpB1Util.text(body, key, "0"));
                if (v > 0L && rowExists(companyId, ft, v) == 0) {
                    return key + " not found";
                }
                if (v <= 0L && create != 0 && (e.table.equals("price_list_items") || e.table.equals("uom_group_items")
                        || e.table.equals("bp_addresses") || e.table.equals("bp_contact_persons"))) {
                    return key + " required";
                }
            } else if (create != 0 && (e.table.equals("price_list_items") || e.table.equals("uom_group_items")
                    || e.table.equals("bp_addresses") || e.table.equals("bp_contact_persons"))) {
                return key + " required";
            }
            k = k + 1;
        }
        if (e.table.equals("business_partners")) {
            if (ErpB1Util.has(body, "kind") != 0 || create != 0) {
                String kind = ErpB1Util.text(body, "kind", "");
                if (kind.equals("CUSTOMER") == false && kind.equals("SUPPLIER") == false && kind.equals("BOTH") == false) {
                    return "kind must be CUSTOMER/SUPPLIER/BOTH";
                }
            }
            if (ErpB1Util.has(body, "status") != 0) {
                String st = ErpB1Util.text(body, "status", "");
                if (st.equals("ACTIVE") == false && st.equals("FROZEN") == false && st.equals("INACTIVE") == false) {
                    return "status must be ACTIVE/FROZEN/INACTIVE";
                }
            }
            if (ErpNum.parseLong(ErpB1Util.text(body, "creditLimitCents", "0")) < 0L) {
                return "creditLimitCents must be >= 0";
            }
            if (ErpNum.parseLong(ErpB1Util.text(body, "discountBps", "0")) < 0L
                    || ErpNum.parseLong(ErpB1Util.text(body, "discountBps", "0")) > 10000L) {
                return "discountBps must be 0..10000";
            }
        }
        if (e.table.equals("items")) {
            if (ErpB1Util.has(body, "itemType") != 0) {
                String t = ErpB1Util.text(body, "itemType", "");
                if (t.equals("ITEM") == false && t.equals("SERVICE") == false && t.equals("LABOR") == false
                        && t.equals("TRAVEL") == false) {
                    return "itemType must be ITEM/SERVICE/LABOR/TRAVEL";
                }
            }
            if (ErpB1Util.has(body, "manageMethod") != 0) {
                String m = ErpB1Util.text(body, "manageMethod", "");
                if (m.equals("N") == false && m.equals("B") == false && m.equals("S") == false) {
                    return "manageMethod must be N/B/S";
                }
            }
            if (ErpB1Util.has(body, "status") != 0) {
                String st = ErpB1Util.text(body, "status", "");
                if (st.equals("ACTIVE") == false && st.equals("INACTIVE") == false) {
                    return "status must be ACTIVE/INACTIVE";
                }
            }
            if (ErpNum.parseLong(ErpB1Util.text(body, "unitPriceCents", "0")) < 0L) {
                return "unitPriceCents must be >= 0";
            }
        }
        if (e.table.equals("tax_codes")) {
            long r = ErpNum.parseLong(ErpB1Util.text(body, "rateBps", "0"));
            if (r < 0L || r > 10000L) {
                return "rateBps must be 0..10000";
            }
            if (ErpB1Util.has(body, "direction") != 0) {
                String d = ErpB1Util.text(body, "direction", "");
                if (d.equals("OUT") == false && d.equals("IN") == false && d.equals("BOTH") == false) {
                    return "direction must be OUT/IN/BOTH";
                }
            }
        }
        if (e.table.equals("payment_terms")) {
            long nd = ErpNum.parseLong(ErpB1Util.text(body, "netDays", "0"));
            if (nd < 0L || nd > 3650L) {
                return "netDays must be 0..3650";
            }
            long db = ErpNum.parseLong(ErpB1Util.text(body, "discountBps", "0"));
            if (db < 0L || db > 10000L) {
                return "discountBps must be 0..10000";
            }
        }
        if (e.table.equals("currencies")) {
            long dc = ErpNum.parseLong(ErpB1Util.text(body, "decimals", "2"));
            if (dc < 0L || dc > 4L) {
                return "decimals must be 0..4";
            }
        }
        if (e.table.equals("exchange_rates")) {
            String cc = ErpB1Util.text(body, "currencyCode", "");
            ArrayList<Object> p = ErpB1Util.params();
            p.add(ErpB1Util.lng(companyId));
            p.add(cc);
            if (ErpDb.queryOne("SELECT id FROM currencies WHERE company_id=? AND code=?", p, new LongMapper()) == null) {
                return "currency not found: " + cc;
            }
            if (ErpNum.parseLong(ErpB1Util.text(body, "rateMicro", "0")) <= 0L) {
                return "rateMicro must be > 0";
            }
            if (ErpNum.parseLong(ErpB1Util.text(body, "rateDate", "0")) <= 0L && create != 0) {
                return "rateDate required";
            }
        }
        if (e.table.equals("price_lists")) {
            if (ErpB1Util.has(body, "factorBps") != 0) {
                long fb = ErpNum.parseLong(ErpB1Util.text(body, "factorBps", "10000"));
                if (fb <= 0L || fb > 1000000L) {
                    return "factorBps must be 1..1000000";
                }
            }
            long base = ErpNum.parseLong(ErpB1Util.text(body, "baseListId", "0"));
            if (base > 0L && rowExists(companyId, "price_lists", base) == 0) {
                return "baseListId not found";
            }
            if (base > 0L && base == selfId) {
                return "price list cannot be its own base";
            }
        }
        if (e.table.equals("price_list_items")) {
            if (ErpNum.parseLong(ErpB1Util.text(body, "priceCents", "0")) < 0L) {
                return "priceCents must be >= 0";
            }
        }
        if (e.table.equals("uom_group_items")) {
            if (ErpNum.parseLong(ErpB1Util.text(body, "baseQty", "1")) < 1L) {
                return "baseQty must be >= 1";
            }
        }
        return "";
    }

    /** Returns id of an existing row that matches the entity key columns (for upsert), or 0. */
    static long findByKey(long companyId, B1Entity e, JsonObject body) {
        if (e.keys.size() == 0) {
            return 0L;
        }
        ArrayList<Object> p = ErpB1Util.params();
        p.add(ErpB1Util.lng(companyId));
        String sql = "SELECT id FROM " + e.table + " WHERE company_id=?";
        int k = 0;
        while (k < e.keys.size()) {
            String c = (String) e.keys.get(k);
            sql = sql + " AND " + c + "=?";
            p.add(ErpB1Util.text(body, ErpB1Util.camel(c), ""));
            k = k + 1;
        }
        return ErpNum.asLong(ErpDb.queryOne(sql, p, new LongMapper()));
    }

    /** Create or update; returns JSON of the row. Throws RuntimeException on validation errors. */
    static String save(long companyId, B1Entity e, long id, JsonObject body) {
        long selfId = id;
        int create = 0;
        if (id <= 0L) {
            create = 1;
            long ex = findByKey(companyId, e, body);
            if (ex > 0L) {
                id = ex;
                create = 0;
                selfId = ex;
            }
        }
        String bad = validate(companyId, e, body, create, selfId);
        if (bad.length() > 0) {
            throw new RuntimeException(bad);
        }
        if (create == 0 && rowExists(companyId, e.table, id) == 0) {
            throw new RuntimeException("not found");
        }
        long now = AsyncClockNative.wallMillis();
        if (create != 0) {
            String names = "company_id";
            String marks = "?";
            ArrayList<Object> p = ErpB1Util.params();
            p.add(ErpB1Util.lng(companyId));
            int i = 0;
            while (i < e.cols.size()) {
                String c = (String) e.cols.get(i);
                if (ErpB1Util.has(body, ErpB1Util.camel(c)) != 0) {
                    names = names + ", " + c;
                    marks = marks + ", ?";
                    p.add(ErpB1Util.text(body, ErpB1Util.camel(c), ""));
                }
                i = i + 1;
            }
            if (e.stamp != 0) {
                names = names + ", created_at";
                marks = marks + ", ?";
                p.add(ErpB1Util.lng(now));
            }
            try {
                id = insertId(e.table, names, marks, p);
            } catch (Exception ex2) {
                if (ErpB1Util.isUnique(ex2.getMessage()) != 0) {
                    throw new RuntimeException("DUPLICATE_KEY");
                }
                throw ex2;
            }
        } else {
            String set = "";
            ArrayList<Object> p = ErpB1Util.params();
            int i = 0;
            while (i < e.cols.size()) {
                String c = (String) e.cols.get(i);
                if (ErpB1Util.has(body, ErpB1Util.camel(c)) != 0) {
                    if (set.length() > 0) {
                        set = set + ", ";
                    }
                    set = set + c + "=?";
                    p.add(ErpB1Util.text(body, ErpB1Util.camel(c), ""));
                }
                i = i + 1;
            }
            if (set.length() > 0) {
                p.add(ErpB1Util.lng(id));
                p.add(ErpB1Util.lng(companyId));
                try {
                    ErpDb.exec("UPDATE " + e.table + " SET " + set + " WHERE id=? AND company_id=?", p);
                } catch (Exception ex3) {
                    if (ErpB1Util.isUnique(ex3.getMessage()) != 0) {
                        throw new RuntimeException("DUPLICATE_KEY");
                    }
                    throw ex3;
                }
            }
        }
        if (e.table.equals("items") && ErpB1Util.has(body, "itemType") != 0
                && ErpB1Util.text(body, "itemType", "ITEM").equals("ITEM") == false
                && ErpB1Util.has(body, "isInventory") == 0) {
            ArrayList<Object> ip = ErpB1Util.params();
            ip.add(ErpB1Util.lng(id));
            ip.add(ErpB1Util.lng(companyId));
            ErpDb.exec("UPDATE items SET is_inventory=0 WHERE id=? AND company_id=?", ip);
        }
        if (e.table.equals("price_lists") && ErpB1Util.text(body, "isDefault", "0").equals("1")) {
            ArrayList<Object> up = ErpB1Util.params();
            up.add(ErpB1Util.lng(companyId));
            up.add(ErpB1Util.lng(id));
            ErpDb.exec("UPDATE price_lists SET is_default=0 WHERE company_id=? AND id<>?", up);
        }
        return get(companyId, e, id);
    }

    static long insertId(String table, String names, String marks, ArrayList<Object> p) {
        String sql = "INSERT INTO " + table + " (" + names + ") VALUES (" + marks + ")";
        if (Erp.dialect.equals("sqlite")) {
            ErpDb.exec(sql, p);
            return ErpNum.asLong(ErpDb.queryOne("SELECT last_insert_rowid()", ErpB1Util.params(), new LongMapper()));
        }
        DbQueryResult r = ErpDb.query(sql + " RETURNING id", p);
        if (r.rows.size() == 0) {
            return 0L;
        }
        return r.rows.get(0).getLong(0);
    }

    static void remove(long companyId, B1Entity e, long id) {
        if (e.table.equals("price_list_items") || e.table.equals("uom_group_items")
                || e.table.equals("bp_addresses") || e.table.equals("bp_contact_persons")
                || e.table.equals("exchange_rates")) {
            ArrayList<Object> p = ErpB1Util.params();
            p.add(ErpB1Util.lng(id));
            p.add(ErpB1Util.lng(companyId));
            ErpDb.exec("DELETE FROM " + e.table + " WHERE id=? AND company_id=?", p);
            return;
        }
        throw new RuntimeException("master records cannot be deleted; set active/status instead");
    }

    /* ---------------------------------------------------------------- defaults / upgrade */

    static int codeExists(long companyId, String table, String col, String code) {
        ArrayList<Object> p = ErpB1Util.params();
        p.add(ErpB1Util.lng(companyId));
        p.add(code);
        Object v = ErpDb.queryOne("SELECT id FROM " + table + " WHERE company_id=? AND " + col + "=?", p, new LongMapper());
        if (v == null) {
            return 0;
        }
        return 1;
    }

    static long idByCode(long companyId, String table, String col, String code) {
        ArrayList<Object> p = ErpB1Util.params();
        p.add(ErpB1Util.lng(companyId));
        p.add(code);
        return ErpNum.asLong(ErpDb.queryOne("SELECT id FROM " + table + " WHERE company_id=? AND " + col + "=?", p,
                new LongMapper()));
    }

    static void addTerm(long cid, String code, String name, int days, int discDays, int discBps, long now) {
        if (codeExists(cid, "payment_terms", "code", code) != 0) {
            return;
        }
        ArrayList<Object> p = ErpB1Util.params();
        p.add(ErpB1Util.lng(cid));
        p.add(code);
        p.add(name);
        p.add(String.valueOf(days));
        p.add(String.valueOf(discDays));
        p.add(String.valueOf(discBps));
        p.add(ErpB1Util.lng(now));
        ErpDb.exec("INSERT INTO payment_terms(company_id, code, name, net_days, discount_days, discount_bps, created_at) VALUES (?,?,?,?,?,?,?)", p);
    }

    static void addTax(long cid, String code, String name, int bps, long now) {
        if (codeExists(cid, "tax_codes", "code", code) != 0) {
            return;
        }
        ArrayList<Object> p = ErpB1Util.params();
        p.add(ErpB1Util.lng(cid));
        p.add(code);
        p.add(name);
        p.add(String.valueOf(bps));
        p.add(ErpB1Util.lng(now));
        ErpDb.exec("INSERT INTO tax_codes(company_id, code, name, rate_bps, direction, created_at) VALUES (?,?,?,?,'BOTH',?)", p);
    }

    static void addCurrency(long cid, String code, String name, String sym, int dec, int local, long now) {
        if (codeExists(cid, "currencies", "code", code) != 0) {
            return;
        }
        ArrayList<Object> p = ErpB1Util.params();
        p.add(ErpB1Util.lng(cid));
        p.add(code);
        p.add(name);
        p.add(sym);
        p.add(String.valueOf(dec));
        p.add(String.valueOf(local));
        p.add(ErpB1Util.lng(now));
        ErpDb.exec("INSERT INTO currencies(company_id, code, name, symbol, decimals, is_local, created_at) VALUES (?,?,?,?,?,?,?)", p);
    }

    static void addGroup(long cid, String table, String code, String name, String kind, long now) {
        if (codeExists(cid, table, "code", code) != 0) {
            return;
        }
        ArrayList<Object> p = ErpB1Util.params();
        p.add(ErpB1Util.lng(cid));
        p.add(code);
        p.add(name);
        if (kind.length() > 0) {
            p.add(kind);
            p.add(ErpB1Util.lng(now));
            ErpDb.exec("INSERT INTO " + table + "(company_id, code, name, kind, created_at) VALUES (?,?,?,?,?)", p);
        } else {
            p.add(ErpB1Util.lng(now));
            ErpDb.exec("INSERT INTO " + table + "(company_id, code, name, created_at) VALUES (?,?,?,?)", p);
        }
    }

    static String localCurrency(long companyId) {
        ArrayList<Object> p = ErpB1Util.params();
        p.add(ErpB1Util.lng(companyId));
        Object v = null;
        try {
            v = ErpDb.queryOne("SELECT functional_currency FROM companies WHERE id=?", p, new StringMapper());
        } catch (Exception ex) {
            v = null;
        }
        if (v == null || ((String) v).length() == 0) {
            return "CNY";
        }
        return (String) v;
    }

    static void ensureB1Accounts(long cid) {
        ensureAcct(cid, "1500", "Input VAT", AccountType.ASSET, NormalBalance.DEBIT, "INPUT_TAX");
        ensureAcct(cid, "2200", "Output VAT", AccountType.LIABILITY, NormalBalance.CREDIT, "OUTPUT_TAX");
        ensureAcct(cid, "5100", "Purchase Price Variance", AccountType.EXPENSE, NormalBalance.DEBIT, "PURCHASE_VARIANCE");
        ensureAcct(cid, "6000", "Operating Expense", AccountType.EXPENSE, NormalBalance.DEBIT, "EXPENSE");
        ensureAcct(cid, "4100", "Sales Returns and Allowances", AccountType.REVENUE, NormalBalance.CREDIT, "SALES_RETURNS");
        ensureAcct(cid, "7000", "Exchange Rate Difference", AccountType.EXPENSE, NormalBalance.DEBIT, "FX_DIFF");
    }

    static void ensureAcct(long cid, String code, String name, String type, String normal, String mapKey) {
        if (ErpNum.asLong(ErpFinanceCore.accountIdByCode(cid, code)) == 0L) {
            ErpFinanceCore.ensureAccount(cid, code, name, type, 0L);
            ArrayList<Object> p = ErpB1Util.params();
            p.add(normal);
            p.add(ErpB1Util.lng(cid));
            p.add(code);
            try {
                ErpDb.exec("UPDATE gl_accounts SET normal_balance=?, is_control=0, require_partner=0 WHERE company_id=? AND account_code=?", p);
            } catch (Exception ignored) {
            }
        }
        if (ErpFinanceCore.mappedAccountId(cid, mapKey) == 0L) {
            ErpFinanceCore.mapAccount(cid, mapKey, code);
        }
    }

    static ArrayList<Object> defaultsDone;

    /** Seed B1 defaults once per company at login so the first parallel UI requests do not race on it. */
    static void seedOnLogin(long cid) {
        try {
            ensureDefaultsOnce(cid);
        } catch (Exception ex) {
            System.out.println("B1_SEED_WARN " + ErpB1Util.exMsg(ex, "seed failed"));
        }
    }

    /** Forget the seeded-company cache (after a demo reset or restore wipes the tables). */
    static void resetDefaultsCache() {
        defaultsDone = new ArrayList<Object>();
    }

    static void ensureDefaultsOnce(long cid) {
        if (defaultsDone == null) {
            defaultsDone = new ArrayList<Object>();
        }
        String key = ErpB1Util.lng(cid);
        int i = 0;
        while (i < defaultsDone.size()) {
            if (((String) defaultsDone.get(i)).equals(key)) {
                return;
            }
            i = i + 1;
        }
        /* Concurrent first requests may race on check-then-insert; the seeding is idempotent, so retry. */
        int attempt = 0;
        while (attempt < 6) {
            try {
                ensureDefaults(cid);
                attempt = 99;
            } catch (Exception ex) {
                String m = ErpB1Util.exMsg(ex, "");
                if ((ErpB1Util.isUnique(m) == 0 && m.indexOf("locked") < 0 && m.indexOf("in progress") < 0) || attempt >= 5) {
                    throw ex;
                }
                attempt = attempt + 1;
            }
        }
        defaultsDone.add(key);
    }

    /** Idempotent: seeds master defaults and backfills legacy rows for one company. */
    static void ensureDefaults(long cid) {
        long now = AsyncClockNative.wallMillis();
        String cs = ErpB1Util.lng(cid);
        addTerm(cid, "NET0", "Immediate", 0, 0, 0, now);
        addTerm(cid, "NET30", "Net 30 days", 30, 0, 0, now);
        addTerm(cid, "NET60", "Net 60 days", 60, 0, 0, now);
        addTerm(cid, "2/10N30", "2% 10 days, net 30", 30, 10, 200, now);
        addTax(cid, "VAT0", "Zero rate / exempt", 0, now);
        addTax(cid, "VAT6", "VAT 6%", 600, now);
        addTax(cid, "VAT9", "VAT 9%", 900, now);
        addTax(cid, "VAT13", "VAT 13%", 1300, now);
        String local = localCurrency(cid);
        addCurrency(cid, "CNY", "Chinese Yuan", "CNY", 2, 0, now);
        addCurrency(cid, "USD", "US Dollar", "$", 2, 0, now);
        addCurrency(cid, "EUR", "Euro", "EUR", 2, 0, now);
        addCurrency(cid, "HKD", "Hong Kong Dollar", "HK$", 2, 0, now);
        addCurrency(cid, "JPY", "Japanese Yen", "JPY", 0, 0, now);
        addCurrency(cid, "GBP", "Pound Sterling", "GBP", 2, 0, now);
        addCurrency(cid, local, local, local, 2, 0, now);
        ArrayList<Object> lp = ErpB1Util.params();
        lp.add(local);
        lp.add(ErpB1Util.lng(cid));
        ErpDb.exec("UPDATE currencies SET is_local=CASE WHEN code=? THEN 1 ELSE 0 END WHERE company_id=?", lp);
        addGroup(cid, "bp_groups", "CUS-DEF", "Customers", "CUSTOMER", now);
        addGroup(cid, "bp_groups", "SUP-DEF", "Suppliers", "SUPPLIER", now);
        addGroup(cid, "bp_groups", "LEAD", "Leads", "CUSTOMER", now);
        addGroup(cid, "item_groups", "GEN", "General Items", "", now);
        addGroup(cid, "uom_groups", "UOM-EA", "Each (EA)", "", now);
        if (codeExists(cid, "price_lists", "code", "BASE") == 0) {
            ArrayList<Object> p = ErpB1Util.params();
            p.add(cs);
            p.add(local);
            p.add(ErpB1Util.lng(now));
            ErpDb.exec("INSERT INTO price_lists(company_id, code, name, currency_code, factor_bps, is_default, created_at) VALUES (?, 'BASE', 'Base Price List', ?, 10000, 1, ?)", p);
        }
        long uomEa = idByCode(cid, "uom_groups", "code", "UOM-EA");
        if (uomEa > 0L) {
            ArrayList<Object> p = ErpB1Util.params();
            p.add(cs);
            p.add(ErpB1Util.lng(uomEa));
            Object has = ErpDb.queryOne("SELECT id FROM uom_group_items WHERE company_id=? AND uom_group_id=? AND uom_code='EA'", p, new LongMapper());
            if (has == null) {
                ErpDb.exec("INSERT INTO uom_group_items(company_id, uom_group_id, uom_code, base_qty) VALUES (?,?, 'EA', 1)", p);
            }
        }
        /* item categories → item groups */
        try {
            ArrayList<Object> cp = ErpB1Util.params();
            cp.add(cs);
            DbQueryResult cats = ErpDb.query("SELECT code, name FROM item_categories WHERE company_id=?", cp);
            int i = 0;
            while (i < cats.rows.size()) {
                addGroup(cid, "item_groups", cats.rows.get(i).getString(0), cats.rows.get(i).getString(1), "", now);
                i = i + 1;
            }
        } catch (Exception ignored) {
        }
        long net30 = idByCode(cid, "payment_terms", "code", "NET30");
        long cusGrp = idByCode(cid, "bp_groups", "code", "CUS-DEF");
        long supGrp = idByCode(cid, "bp_groups", "code", "SUP-DEF");
        long genGrp = idByCode(cid, "item_groups", "code", "GEN");
        long baseList = idByCode(cid, "price_lists", "code", "BASE");
        ArrayList<Object> u1 = ErpB1Util.params();
        u1.add(ErpB1Util.lng(cusGrp));
        u1.add(cs);
        ErpDb.exec("UPDATE business_partners SET group_id=? WHERE company_id=? AND group_id=0 AND kind<>'SUPPLIER'", u1);
        ArrayList<Object> u2 = ErpB1Util.params();
        u2.add(ErpB1Util.lng(supGrp));
        u2.add(cs);
        ErpDb.exec("UPDATE business_partners SET group_id=? WHERE company_id=? AND group_id=0 AND kind='SUPPLIER'", u2);
        ArrayList<Object> u3 = ErpB1Util.params();
        u3.add(ErpB1Util.lng(net30));
        u3.add(cs);
        ErpDb.exec("UPDATE business_partners SET payment_terms_id=? WHERE company_id=? AND payment_terms_id=0", u3);
        ArrayList<Object> u4 = ErpB1Util.params();
        u4.add(ErpB1Util.lng(baseList));
        u4.add(cs);
        ErpDb.exec("UPDATE business_partners SET price_list_id=? WHERE company_id=? AND price_list_id=0 AND kind<>'SUPPLIER'", u4);
        ArrayList<Object> u5 = ErpB1Util.params();
        u5.add(cs);
        u5.add(cs);
        ErpDb.exec("UPDATE items SET item_group_id=COALESCE((SELECT g.id FROM item_groups g WHERE g.company_id=? AND g.code=items.category_code),0) WHERE company_id=? AND item_group_id=0", u5);
        ArrayList<Object> u6 = ErpB1Util.params();
        u6.add(ErpB1Util.lng(genGrp));
        u6.add(cs);
        ErpDb.exec("UPDATE items SET item_group_id=? WHERE company_id=? AND item_group_id=0", u6);
        ArrayList<Object> u7 = ErpB1Util.params();
        u7.add(ErpB1Util.lng(uomEa));
        u7.add(cs);
        ErpDb.exec("UPDATE items SET uom_group_id=? WHERE company_id=? AND uom_group_id=0", u7);
        ArrayList<Object> u8 = ErpB1Util.params();
        u8.add(cs);
        ErpDb.exec("UPDATE items SET sales_uom=uom, purchase_uom=uom WHERE company_id=? AND sales_uom='' AND uom<>''", u8);
        ensureB1Accounts(cid);
    }

    /* ---------------------------------------------------------------- price / tax / fx / credit */

    static long baseItemPrice(long companyId, long itemId) {
        ArrayList<Object> p = ErpB1Util.params();
        p.add(ErpB1Util.lng(itemId));
        p.add(ErpB1Util.lng(companyId));
        return ErpNum.asLong(ErpDb.queryOne("SELECT unit_price_cents FROM items WHERE id=? AND company_id=?", p, new LongMapper()));
    }

    /** Price from a price list (recursive through base list, with factor); -1 when no price found. */
    static long listPrice(long companyId, long listId, long itemId, int depth) {
        if (listId <= 0L || depth > 4) {
            return -1L;
        }
        ArrayList<Object> p = ErpB1Util.params();
        p.add(ErpB1Util.lng(listId));
        p.add(ErpB1Util.lng(companyId));
        DbQueryResult hr = ErpDb.query("SELECT base_list_id, factor_bps FROM price_lists WHERE id=? AND company_id=? AND active=1", p);
        if (hr.rows.size() == 0) {
            return -1L;
        }
        long baseId = hr.rows.get(0).getLong(0);
        long factor = hr.rows.get(0).getLong(1);
        ArrayList<Object> q = ErpB1Util.params();
        q.add(ErpB1Util.lng(listId));
        q.add(ErpB1Util.lng(itemId));
        q.add(ErpB1Util.lng(companyId));
        Object own = ErpDb.queryOne("SELECT price_cents FROM price_list_items WHERE price_list_id=? AND item_id=? AND company_id=?", q, new LongMapper());
        if (own != null) {
            return ErpNum.asLong(own);
        }
        long base = -1L;
        if (baseId > 0L) {
            base = listPrice(companyId, baseId, itemId, depth + 1);
        }
        if (base < 0L) {
            base = baseItemPrice(companyId, itemId);
        }
        return ErpDocMoney.divHalfUp(base * factor, 10000L);
    }

    static long defaultListId(long companyId) {
        ArrayList<Object> p = ErpB1Util.params();
        p.add(ErpB1Util.lng(companyId));
        return ErpNum.asLong(ErpDb.queryOne("SELECT id FROM price_lists WHERE company_id=? AND is_default=1 AND active=1 ORDER BY id LIMIT 1", p, new LongMapper()));
    }

    static long lastPurchasePrice(long companyId, long supplierId, long itemId) {
        ArrayList<Object> p = ErpB1Util.params();
        p.add(ErpB1Util.lng(companyId));
        p.add(ErpB1Util.lng(supplierId));
        p.add(ErpB1Util.lng(itemId));
        Object v = ErpDb.queryOne("SELECT l.unit_price_cents FROM purchase_order_lines l JOIN purchase_orders o ON o.id=l.purchase_order_id WHERE o.company_id=? AND o.supplier_id=? AND l.item_id=? ORDER BY l.id DESC LIMIT 1", p, new LongMapper());
        if (v == null) {
            return -1L;
        }
        return ErpNum.asLong(v);
    }

    static String pricingJson(long companyId, long partnerId, long itemId, int sales) {
        B1Price pr = priceInfo(companyId, partnerId, itemId, sales);
        return "{\"itemId\":" + ErpB1Util.lng(itemId) + ",\"unitPriceCents\":" + ErpB1Util.lng(pr.priceCents)
                + ",\"source\":" + ErpJson.str(pr.source) + ",\"discountBps\":" + ErpB1Util.lng(pr.discountBps)
                + ",\"taxCodeId\":" + ErpB1Util.lng(pr.taxCodeId) + ",\"taxCode\":" + ErpJson.str(pr.taxCode)
                + ",\"taxBps\":" + ErpB1Util.lng(pr.taxBps) + ",\"uom\":" + ErpJson.str(pr.uom)
                + ",\"isInventory\":" + ErpB1Util.lng(pr.isInventory)
                + ",\"currency\":" + ErpJson.str(pr.currency) + "}";
    }

    static B1Price priceInfo(long companyId, long partnerId, long itemId, int sales) {
        ArrayList<Object> ip = ErpB1Util.params();
        ip.add(ErpB1Util.lng(itemId));
        ip.add(ErpB1Util.lng(companyId));
        DbQueryResult ir = ErpDb.query("SELECT unit_price_cents, sales_tax_code_id, purchase_tax_code_id, COALESCE(sales_uom,''), COALESCE(purchase_uom,''), uom, is_inventory FROM items WHERE id=? AND company_id=?", ip);
        if (ir.rows.size() == 0) {
            throw new RuntimeException("item not found");
        }
        Row irow = ir.rows.get(0);
        long taxCodeId = irow.getLong(1);
        String uom = irow.getString(3);
        if (sales == 0) {
            taxCodeId = irow.getLong(2);
            uom = irow.getString(4);
        }
        if (uom == null || uom.length() == 0) {
            uom = irow.getString(5);
        }
        long listId = 0L;
        long discount = 0L;
        long partnerTax = 0L;
        String partnerCurrency = "";
        if (partnerId > 0L) {
            ArrayList<Object> pp = ErpB1Util.params();
            pp.add(ErpB1Util.lng(partnerId));
            pp.add(ErpB1Util.lng(companyId));
            DbQueryResult pr = ErpDb.query("SELECT price_list_id, discount_bps, default_tax_code_id, currency FROM business_partners WHERE id=? AND company_id=?", pp);
            if (pr.rows.size() > 0) {
                listId = pr.rows.get(0).getLong(0);
                discount = pr.rows.get(0).getLong(1);
                partnerTax = pr.rows.get(0).getLong(2);
                partnerCurrency = pr.rows.get(0).getString(3);
            }
        }
        long price = -1L;
        String source = "ITEM";
        if (sales != 0) {
            if (listId <= 0L) {
                listId = defaultListId(companyId);
            }
            price = listPrice(companyId, listId, itemId, 0);
            if (price >= 0L) {
                source = "PRICE_LIST";
            }
        } else {
            discount = 0L;
            if (partnerId > 0L) {
                price = lastPurchasePrice(companyId, partnerId, itemId);
                if (price >= 0L) {
                    source = "LAST_PURCHASE";
                }
            }
        }
        if (price < 0L) {
            price = irow.getLong(0);
            source = "ITEM";
        }
        if (taxCodeId <= 0L) {
            taxCodeId = partnerTax;
        }
        long taxBps = 0L;
        String taxCode = "";
        if (taxCodeId > 0L) {
            ArrayList<Object> tp = ErpB1Util.params();
            tp.add(ErpB1Util.lng(taxCodeId));
            tp.add(ErpB1Util.lng(companyId));
            DbQueryResult tr = ErpDb.query("SELECT rate_bps, code FROM tax_codes WHERE id=? AND company_id=?", tp);
            if (tr.rows.size() > 0) {
                taxBps = tr.rows.get(0).getLong(0);
                taxCode = tr.rows.get(0).getString(1);
            }
        }
        B1Price out = new B1Price();
        out.priceCents = price;
        out.source = source;
        out.discountBps = discount;
        out.taxCodeId = taxCodeId;
        out.taxCode = taxCode;
        out.taxBps = taxBps;
        out.uom = uom;
        out.isInventory = irow.getLong(6);
        out.currency = partnerCurrency;
        return out;
    }

    /** Units of exchange rate to local currency in micro units (1.000000 = 1000000); 0 if missing. */
    static long rateMicro(long companyId, String currency, long asOf) {
        if (currency == null || currency.length() == 0 || currency.equals(localCurrency(companyId))) {
            return 1000000L;
        }
        ArrayList<Object> p = ErpB1Util.params();
        p.add(ErpB1Util.lng(companyId));
        p.add(currency);
        p.add(ErpB1Util.lng(asOf));
        Object v = ErpDb.queryOne("SELECT rate_micro FROM exchange_rates WHERE company_id=? AND currency_code=? AND rate_date<=? ORDER BY rate_date DESC, id DESC LIMIT 1", p, new LongMapper());
        if (v == null) {
            return 0L;
        }
        return ErpNum.asLong(v);
    }

    static long toLocal(long amount, long rateMicro) {
        return ErpDocMoney.divHalfUp(amount * rateMicro, 1000000L);
    }

    /** Outstanding AR + open (unbilled) sales orders vs credit limit. */
    static String creditJson(long companyId, long partnerId, long extraCents) {
        ArrayList<Object> p = ErpB1Util.params();
        p.add(ErpB1Util.lng(partnerId));
        p.add(ErpB1Util.lng(companyId));
        DbQueryResult r = ErpDb.query("SELECT credit_limit_cents, commitment_limit_cents, status, name FROM business_partners WHERE id=? AND company_id=?", p);
        if (r.rows.size() == 0) {
            throw new RuntimeException("partner not found");
        }
        long limit = r.rows.get(0).getLong(0);
        long commit = r.rows.get(0).getLong(1);
        String status = r.rows.get(0).getString(2);
        ArrayList<Object> q = ErpB1Util.params();
        q.add(ErpB1Util.lng(companyId));
        q.add(ErpB1Util.lng(partnerId));
        long balance = ErpNum.asLong(ErpDb.queryOne("SELECT COALESCE(SUM(open_amount),0) FROM ar_invoices WHERE company_id=? AND customer_id=? AND status<>'DRAFT' AND status<>'REVERSED' AND status<>'CANCELLED'", q, new LongMapper()));
        long ordersOpen = openOrderCents(companyId, partnerId);
        long exposure = balance + ordersOpen + extraCents;
        int blocked = 0;
        String reason = "";
        if (status != null && status.equals("FROZEN")) {
            blocked = 1;
            reason = "PARTNER_FROZEN";
        } else if (status != null && status.equals("INACTIVE")) {
            blocked = 1;
            reason = "PARTNER_INACTIVE";
        } else if (limit > 0L && balance + extraCents > limit) {
            blocked = 1;
            reason = "CREDIT_LIMIT_EXCEEDED";
        } else if (commit > 0L && exposure > commit) {
            blocked = 1;
            reason = "COMMITMENT_LIMIT_EXCEEDED";
        }
        return "{\"partnerId\":" + ErpB1Util.lng(partnerId) + ",\"creditLimitCents\":" + ErpB1Util.lng(limit)
                + ",\"commitmentLimitCents\":" + ErpB1Util.lng(commit) + ",\"balanceCents\":" + ErpB1Util.lng(balance)
                + ",\"openOrdersCents\":" + ErpB1Util.lng(ordersOpen) + ",\"extraCents\":" + ErpB1Util.lng(extraCents)
                + ",\"exposureCents\":" + ErpB1Util.lng(exposure) + ",\"blocked\":" + String.valueOf(blocked)
                + ",\"reason\":" + ErpJson.str(reason) + "}";
    }

    static long openOrderCents(long companyId, long partnerId) {
        ArrayList<Object> p = ErpB1Util.params();
        p.add(ErpB1Util.lng(companyId));
        p.add(ErpB1Util.lng(partnerId));
        Object v = null;
        try {
            v = ErpDb.queryOne("SELECT COALESCE(SUM((l.quantity - l.delivered_qty) * l.unit_price_cents),0) FROM sales_order_lines l JOIN sales_orders o ON o.id=l.sales_order_id WHERE o.company_id=? AND o.customer_id=? AND o.status IN ('APPROVED','PARTIALLY_DELIVERED')", p, new LongMapper());
        } catch (Exception ex) {
            v = null;
        }
        return ErpNum.asLong(v);
    }

    /** Throws when the partner is blocked for a new sales posting. */
    static void assertCreditOk(long companyId, long partnerId, long extraCents) {
        String j = creditJson(companyId, partnerId, extraCents);
        if (j.indexOf("\"blocked\":1") >= 0) {
            int a = j.indexOf("\"reason\":\"");
            String reason = "CREDIT_BLOCKED";
            if (a >= 0) {
                String tail = j.substring(a + 10, j.length());
                int b = tail.indexOf("\"");
                if (b >= 0) {
                    reason = tail.substring(0, b);
                }
            }
            throw new TransactionException(reason);
        }
    }
}
