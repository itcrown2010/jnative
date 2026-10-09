/**
 * @author 张晓达
 */
class ErpItems {
    static ArrayList<Object> list(long companyId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult r = ErpDb.query(
                "SELECT id, sku, name, unit_price_cents FROM items WHERE company_id=? ORDER BY id",
                p);
        ArrayList<Object> out = new ArrayList<Object>();
        int i = 0;
        while (i < r.rows.size()) {
            Row row = r.rows.get(i);
            ItemDto dto = new ItemDto();
            dto.id = row.getLong(0);
            dto.sku = row.getString(1);
            dto.name = row.getString(2);
            dto.unitPriceCents = row.getLong(3);
            out.add(dto);
            i = i + 1;
        }
        return out;
    }

    static ItemDto get(long companyId, long itemId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(itemId));
        p.add(CryptoParse.longToString(companyId));
        Object v = ErpDb.queryOne(
                "SELECT id, sku, name, unit_price_cents FROM items WHERE id=? AND company_id=?",
                p, new ItemRowMapper());
        if (v == null) {
            return null;
        }
        return (ItemDto) v;
    }

    /** Create item; returns null on validation/duplicate failure (message via outErr). */
    static ItemDto create(long companyId, String sku, String name, long unitPriceCents) {
        if (sku == null || sku.length() == 0 || name == null || name.length() == 0) {
            return null;
        }
        if (unitPriceCents < 0L) {
            return null;
        }
        ArrayList<Object> chk = new ArrayList<Object>();
        chk.add(CryptoParse.longToString(companyId));
        chk.add(sku);
        Object existing = ErpDb.queryOne(
                "SELECT id FROM items WHERE company_id=? AND sku=?", chk, new LongMapper());
        if (existing != null) {
            return null;
        }
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> ins = new ArrayList<Object>();
        ins.add(CryptoParse.longToString(companyId));
        ins.add(sku);
        ins.add(name);
        ins.add(CryptoParse.longToString(unitPriceCents));
        ins.add(CryptoParse.longToString(now));
        ErpDb.exec(
                "INSERT INTO items(company_id, sku, name, unit_price_cents, created_at) VALUES (?,?,?,?,?)",
                ins);
        return getBySku(companyId, sku);
    }

    static ItemDto getBySku(long companyId, String sku) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(sku);
        Object v = ErpDb.queryOne(
                "SELECT id, sku, name, unit_price_cents FROM items WHERE company_id=? AND sku=?",
                p, new ItemRowMapper());
        if (v == null) {
            return null;
        }
        return (ItemDto) v;
    }
}

class ItemRowMapper implements RowMapper {
    public Object map(Row row) {
        ItemDto dto = new ItemDto();
        dto.id = row.getLong(0);
        dto.sku = row.getString(1);
        dto.name = row.getString(2);
        dto.unitPriceCents = row.getLong(3);
        return dto;
    }
}
