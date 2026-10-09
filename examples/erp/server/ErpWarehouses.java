/**
 * @author 张晓达
 */
class ErpWarehouses {
    static ArrayList<Object> list(long companyId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult r = ErpDb.query(
                "SELECT id, code, name FROM warehouses WHERE company_id=? ORDER BY id",
                p);
        ArrayList<Object> out = new ArrayList<Object>();
        int i = 0;
        while (i < r.rows.size()) {
            Row row = r.rows.get(i);
            WarehouseDto dto = new WarehouseDto();
            dto.id = row.getLong(0);
            dto.code = row.getString(1);
            dto.name = row.getString(2);
            out.add(dto);
            i = i + 1;
        }
        return out;
    }
}
