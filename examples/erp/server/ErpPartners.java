/**
 * @author 张晓达
 */
class ErpPartners {
    static ArrayList<Object> list(long companyId, String kind) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        String sql = "SELECT id, code, name, kind FROM business_partners WHERE company_id=? ORDER BY id";
        if (kind != null && kind.length() > 0) {
            sql = "SELECT id, code, name, kind FROM business_partners WHERE company_id=? AND kind=? ORDER BY id";
            p.add(kind);
        }
        DbQueryResult r = ErpDb.query(sql, p);
        ArrayList<Object> out = new ArrayList<Object>();
        int i = 0;
        while (i < r.rows.size()) {
            Row row = r.rows.get(i);
            BusinessPartnerDto dto = new BusinessPartnerDto();
            dto.id = row.getLong(0);
            dto.code = row.getString(1);
            dto.name = row.getString(2);
            dto.kind = row.getString(3);
            out.add(dto);
            i = i + 1;
        }
        return out;
    }
}

class PartnerRowMapper implements RowMapper {
    public Object map(Row row) {
        BusinessPartnerDto dto = new BusinessPartnerDto();
        dto.id = row.getLong(0);
        dto.code = row.getString(1);
        dto.name = row.getString(2);
        dto.kind = row.getString(3);
        return dto;
    }
}
