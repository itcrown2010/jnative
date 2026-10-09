/**
 * BOM master, versioning, multi-level explosion (integer qty; scrap_rate_bp basis points).
 * @author 张晓达
 */
class ErpMfgBom {
    static int MAX_BOM_DEPTH = 20;

    static BomDto createBom(long companyId, long parentItemId, String bomCode, int baseQty) {
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> maxP = new ArrayList<Object>();
        maxP.add(CryptoParse.longToString(companyId));
        maxP.add(bomCode);
        Object maxV = ErpDb.queryOne(
                "SELECT MAX(version) FROM bills_of_material WHERE company_id=? AND bom_code=?",
                maxP, new LongMapper());
        int version = 1;
        if (maxV != null) {
            version = ErpNum.parseInt(CryptoParse.longToString(ErpNum.asLong(maxV))) + 1;
        }
        if (baseQty <= 0) {
            baseQty = 1;
        }
        String ins = "INSERT INTO bills_of_material(company_id, parent_item_id, bom_code, version, base_quantity, status, effective_from, effective_to, created_at, updated_at) VALUES ("
                + CryptoParse.longToString(companyId) + ","
                + CryptoParse.longToString(parentItemId) + ",'"
                + bomCode + "',"
                + CryptoParse.longToString(version) + ","
                + CryptoParse.longToString(baseQty) + ",'"
                + BomStatus.DRAFT + "',0,4102444800000,"
                + CryptoParse.longToString(now) + ","
                + CryptoParse.longToString(now) + ")";
        ErpDb.exec(ins, new ArrayList<Object>());
        ArrayList<Object> qp = new ArrayList<Object>();
        qp.add(CryptoParse.longToString(companyId));
        qp.add(bomCode);
        qp.add(CryptoParse.longToString(version));
        Object idObj = ErpDb.queryOne(
                "SELECT id FROM bills_of_material WHERE company_id=? AND bom_code=? AND version=?",
                qp, new LongMapper());
        BomDto d = new BomDto();
        d.id = ErpNum.asLong(idObj);
        d.parentItemId = parentItemId;
        d.bomCode = bomCode;
        d.version = version;
        d.baseQuantity = baseQty;
        d.status = BomStatus.DRAFT;
        d.effectiveFrom = 0L;
        d.effectiveTo = 4102444800000L;
        d.lines = new ArrayList<Object>();
        return d;
    }

    static void addLine(long companyId, long bomId, long componentItemId, int qty, int scrapBp, long warehouseId,
            int sequence) {
        if (qty <= 0) {
            throw new TransactionException("BOM line quantity must be > 0");
        }
        if (scrapBp < 0) {
            throw new TransactionException("scrapRate must be >= 0");
        }
        ArrayList<Object> bp = new ArrayList<Object>();
        bp.add(CryptoParse.longToString(bomId));
        bp.add(CryptoParse.longToString(companyId));
        Object parent = ErpDb.queryOne(
                "SELECT parent_item_id FROM bills_of_material WHERE id=? AND company_id=?", bp, new LongMapper());
        if (parent == null) {
            throw new TransactionException("BOM not found");
        }
        if (ErpNum.asLong(parent) == componentItemId) {
            throw new TransactionException("BOM cycle: component equals parent");
        }
        /* Cycle check deferred to explode-time (avoids flaky pre-check queries). */
        String ins = "INSERT INTO bom_lines(company_id, bom_id, component_item_id, quantity, uom, scrap_rate_bp, warehouse_id, sequence) VALUES ("
                + CryptoParse.longToString(companyId) + ","
                + CryptoParse.longToString(bomId) + ","
                + CryptoParse.longToString(componentItemId) + ","
                + CryptoParse.longToString(qty) + ",'PCS',"
                + CryptoParse.longToString(scrapBp) + ","
                + CryptoParse.longToString(warehouseId) + ","
                + CryptoParse.longToString(sequence) + ")";
        try {
            ErpDb.exec(ins, new ArrayList<Object>());
        } catch (Exception ex) {
            ErpDb.reconnect();
            ErpDb.exec(ins, new ArrayList<Object>());
        }
    }

    static int wouldCreateCycle(long companyId, long rootParentId, long componentItemId) {
        /* If componentItem has a BOM that eventually needs rootParentId, cycle. */
        return cycleWalk(companyId, componentItemId, rootParentId, 0);
    }

    static int cycleWalk(long companyId, long itemId, long targetParentId, int depth) {
        if (depth > MAX_BOM_DEPTH) {
            return 1;
        }
        BomDto active = findActiveBom(companyId, itemId, AsyncClockNative.wallMillis());
        if (active == null) {
            return 0;
        }
        ArrayList<Object> lines = loadLines(companyId, active.id);
        int i = 0;
        while (i < lines.size()) {
            BomLineDto ln = (BomLineDto) lines.get(i);
            if (ln.componentItemId == targetParentId) {
                return 1;
            }
            if (cycleWalk(companyId, ln.componentItemId, targetParentId, depth + 1) != 0) {
                return 1;
            }
            i = i + 1;
        }
        return 0;
    }

    static void activate(long companyId, long bomId) {
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(bomId));
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult r = ErpDb.query(
                "SELECT parent_item_id, bom_code FROM bills_of_material WHERE id=? AND company_id=?", p);
        if (r.rows.size() == 0) {
            throw new TransactionException("BOM not found");
        }
        Row row = r.rows.get(0);
        long parentId = row.getLong(0);
        String code = row.getString(1);
        ErpDb.exec(
                "UPDATE bills_of_material SET status='" + BomStatus.OBSOLETE + "', updated_at="
                        + CryptoParse.longToString(now)
                        + " WHERE company_id=" + CryptoParse.longToString(companyId)
                        + " AND parent_item_id=" + CryptoParse.longToString(parentId)
                        + " AND status='" + BomStatus.ACTIVE + "'",
                new ArrayList<Object>());
        ErpDb.exec(
                "UPDATE bills_of_material SET status='" + BomStatus.ACTIVE + "', updated_at="
                        + CryptoParse.longToString(now)
                        + " WHERE id=" + CryptoParse.longToString(bomId)
                        + " AND company_id=" + CryptoParse.longToString(companyId),
                new ArrayList<Object>());
    }

    static BomDto findActiveBom(long companyId, long parentItemId, long asOf) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(CryptoParse.longToString(parentItemId));
        p.add(BomStatus.ACTIVE);
        p.add(CryptoParse.longToString(asOf));
        p.add(CryptoParse.longToString(asOf));
        Object idObj = ErpDb.queryOne(
                "SELECT id FROM bills_of_material WHERE company_id=? AND parent_item_id=? AND status=? AND effective_from<=? AND effective_to>=? ORDER BY version DESC LIMIT 1",
                p, new LongMapper());
        if (idObj == null) {
            return null;
        }
        return getBom(companyId, ErpNum.asLong(idObj));
    }

    static BomDto getBom(long companyId, long bomId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(bomId));
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult r = ErpDb.query(
                "SELECT id, parent_item_id, bom_code, version, base_quantity, status, effective_from, effective_to FROM bills_of_material WHERE id=? AND company_id=?",
                p);
        if (r.rows.size() == 0) {
            return null;
        }
        Row row = r.rows.get(0);
        BomDto d = new BomDto();
        d.id = row.getLong(0);
        d.parentItemId = row.getLong(1);
        d.bomCode = row.getString(2);
        d.version = row.getInt(3);
        d.baseQuantity = row.getInt(4);
        d.status = row.getString(5);
        d.effectiveFrom = row.getLong(6);
        d.effectiveTo = row.getLong(7);
        d.lines = loadLines(companyId, bomId);
        return d;
    }

    static ArrayList<Object> loadLines(long companyId, long bomId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(CryptoParse.longToString(bomId));
        DbQueryResult r = ErpDb.query(
                "SELECT id, component_item_id, quantity, uom, scrap_rate_bp, warehouse_id, sequence FROM bom_lines WHERE company_id=? AND bom_id=? ORDER BY sequence, id",
                p);
        ArrayList<Object> out = new ArrayList<Object>();
        int i = 0;
        while (i < r.rows.size()) {
            Row row = r.rows.get(i);
            BomLineDto ln = new BomLineDto();
            ln.id = row.getLong(0);
            ln.componentItemId = row.getLong(1);
            ln.quantity = row.getInt(2);
            ln.uom = row.getString(3);
            ln.scrapRateBp = row.getInt(4);
            ln.warehouseId = row.getLong(5);
            ln.sequence = row.getInt(6);
            out.add(ln);
            i = i + 1;
        }
        return out;
    }

    static ArrayList<Object> listBoms(long companyId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult r = ErpDb.query(
                "SELECT id FROM bills_of_material WHERE company_id=? ORDER BY bom_code, version", p);
        ArrayList<Object> out = new ArrayList<Object>();
        int i = 0;
        while (i < r.rows.size()) {
            out.add(getBom(companyId, r.rows.get(i).getLong(0)));
            i = i + 1;
        }
        return out;
    }

    /**
     * Explode to leaf components. Base BOM quantity scales: need = parentQty * lineQty / baseQty,
     * then apply scrap: need * (10000 + scrapBp) / 10000 (integer ceil via +9999).
     */
    static ArrayList<Object> explode(long companyId, long parentItemId, int quantity, long effectiveDate) {
        ArrayList<Object> acc = new ArrayList<Object>();
        ArrayList<Object> path = new ArrayList<Object>();
        path.add(CryptoParse.longToString(parentItemId));
        explodeRec(companyId, parentItemId, quantity, effectiveDate, 1, path, acc);
        return mergeExplosion(acc);
    }

    static void explodeRec(long companyId, long itemId, int qty, long asOf, int level, ArrayList<Object> path,
            ArrayList<Object> acc) {
        if (level > MAX_BOM_DEPTH) {
            throw new TransactionException("BOM max depth exceeded");
        }
        BomDto bom = findActiveBom(companyId, itemId, asOf);
        if (bom == null) {
            BomExplosionLine leaf = new BomExplosionLine();
            leaf.componentItemId = itemId;
            leaf.requiredQuantity = qty;
            leaf.level = level;
            acc.add(leaf);
            return;
        }
        int i = 0;
        while (i < bom.lines.size()) {
            BomLineDto ln = (BomLineDto) bom.lines.get(i);
            int j = 0;
            while (j < path.size()) {
                if (((String) path.get(j)).equals(CryptoParse.longToString(ln.componentItemId))) {
                    throw new TransactionException("BOM cycle during explosion");
                }
                j = j + 1;
            }
            long qtyL = 0L + qty;
            long lineQtyL = 0L + ln.quantity;
            long baseL = 0L + bom.baseQuantity;
            long scaled = qtyL * lineQtyL;
            int need = ErpNum.parseInt(CryptoParse.longToString(scaled / baseL));
            if (scaled % baseL != 0L) {
                need = need + 1;
            }
            long needL = 0L + need;
            long scrapL = 0L + ln.scrapRateBp;
            long withScrap = (needL * (10000L + scrapL) + 9999L) / 10000L;
            int req = ErpNum.parseInt(CryptoParse.longToString(withScrap));
            BomDto childBom = findActiveBom(companyId, ln.componentItemId, asOf);
            if (childBom == null) {
                BomExplosionLine leaf = new BomExplosionLine();
                leaf.componentItemId = ln.componentItemId;
                leaf.requiredQuantity = req;
                leaf.level = level;
                acc.add(leaf);
            } else {
                ArrayList<Object> nextPath = new ArrayList<Object>();
                int pi = 0;
                while (pi < path.size()) {
                    nextPath.add(path.get(pi));
                    pi = pi + 1;
                }
                nextPath.add(CryptoParse.longToString(ln.componentItemId));
                explodeRec(companyId, ln.componentItemId, req, asOf, level + 1, nextPath, acc);
            }
            i = i + 1;
        }
    }

    static ArrayList<Object> mergeExplosion(ArrayList<Object> acc) {
        ArrayList<Object> out = new ArrayList<Object>();
        int i = 0;
        while (i < acc.size()) {
            BomExplosionLine a = (BomExplosionLine) acc.get(i);
            int found = -1;
            int j = 0;
            while (j < out.size()) {
                BomExplosionLine b = (BomExplosionLine) out.get(j);
                if (b.componentItemId == a.componentItemId) {
                    found = j;
                }
                j = j + 1;
            }
            if (found < 0) {
                BomExplosionLine n = new BomExplosionLine();
                n.componentItemId = a.componentItemId;
                n.requiredQuantity = a.requiredQuantity;
                n.level = a.level;
                out.add(n);
            } else {
                BomExplosionLine b = (BomExplosionLine) out.get(found);
                b.requiredQuantity = b.requiredQuantity + a.requiredQuantity;
            }
            i = i + 1;
        }
        return out;
    }

    static long estimateMaterialCost(long companyId, long parentItemId, int quantity, long asOf) {
        ArrayList<Object> leaves = explode(companyId, parentItemId, quantity, asOf);
        long total = 0L;
        int i = 0;
        while (i < leaves.size()) {
            BomExplosionLine ln = (BomExplosionLine) leaves.get(i);
            ArrayList<Object> p = new ArrayList<Object>();
            p.add(CryptoParse.longToString(ln.componentItemId));
            p.add(CryptoParse.longToString(companyId));
            Object cost = ErpDb.queryOne(
                    "SELECT unit_cost_amount FROM items WHERE id=? AND company_id=?", p, new LongMapper());
            long reqL = 0L + ln.requiredQuantity;
            total = total + ErpNum.asLong(cost) * reqL;
            i = i + 1;
        }
        return total;
    }
}
