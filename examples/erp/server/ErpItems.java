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
