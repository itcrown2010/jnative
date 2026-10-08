/** CSV row wrapper (jnative cannot cast to ArrayList). */
class CsvRow {
    ArrayList<Object> cells;
}

/**
 * CSV import/export with UTF-8 quote-aware parsing and formula-injection hardening.
 * XLSX not claimed.
 */
class ErpPlatformIo {
    static int MAX_ROWS = 10000;
    static int MAX_CELL = 1024;
    static int MAX_BYTES = 2000000;

    static String exportCsvSafe(ArrayList<Object> headers, ArrayList<Object> cells) {
        String out = csvLine(headers);
        out = out + "\n";
        if (cells != null && cells.size() > 0) {
            out = out + csvLine(cells) + "\n";
        }
        return out;
    }

    static String partnerTemplateZh() {
        ArrayList<Object> h = new ArrayList<Object>();
        h.add("code");
        h.add("name");
        h.add("kind");
        ArrayList<Object> ex = new ArrayList<Object>();
        ex.add("C001");
        ex.add("Sample Customer");
        ex.add("CUSTOMER");
        return "# fields: code,name,kind=CUSTOMER|SUPPLIER|BOTH\n"
                + "# no overwrite by default\n"
                + csvLine(h) + "\n" + csvLine(ex) + "\n";
    }

    static String itemTemplateZh() {
        ArrayList<Object> h = new ArrayList<Object>();
        h.add("SKU");
        h.add("name");
        h.add("priceCents");
        h.add("costCents");
        ArrayList<Object> ex = new ArrayList<Object>();
        ex.add("SKU-001");
        ex.add("Sample Item");
        ex.add("8000");
        ex.add("5000");
        return "# fields: sku,name,priceCents,costCents\n"
                + "# no overwrite by default\n"
                + csvLine(h) + "\n" + csvLine(ex) + "\n";
    }

    static String csvLine(ArrayList<Object> cells) {
        String out = "";
        int ci = 0;
        while (ci < cells.size()) {
            if (ci > 0) {
                out = out + ",";
            }
            out = out + csvCell((String) cells.get(ci));
            ci = ci + 1;
        }
        return out;
    }

    static String csvCell(String raw) {
        if (raw == null) {
            raw = "";
        }
        if (raw.length() > MAX_CELL) {
            String cut = "";
            int i = 0;
            while (i < MAX_CELL) {
                cut = cut + WebUtil.byteChar(raw.charAt(i));
                i = i + 1;
            }
            raw = cut;
        }
        if (raw.length() > 0) {
            int c0 = raw.charAt(0);
            if (c0 == 61 || c0 == 43 || c0 == 45 || c0 == 64) {
                raw = "'" + raw;
            }
        }
        int needQuote = 0;
        int i = 0;
        while (i < raw.length()) {
            int c = raw.charAt(i);
            if (c == 34 || c == 44 || c == 10 || c == 13) {
                needQuote = 1;
            }
            i = i + 1;
        }
        if (needQuote == 0) {
            return raw;
        }
        String esc = "\"";
        int j = 0;
        while (j < raw.length()) {
            int c = raw.charAt(j);
            if (c == 34) {
                esc = esc + "\"\"";
            } else {
                esc = esc + WebUtil.byteChar(c);
            }
            j = j + 1;
        }
        return esc + "\"";
    }

    /** Quote-aware CSV parse: returns ArrayList of ArrayList<String> rows (no comment lines). */
    static ArrayList<Object> parseCsv(String text) {
        ArrayList<Object> rows = new ArrayList<Object>();
        if (text == null) {
            return rows;
        }
        ArrayList<Object> cur = new ArrayList<Object>();
        String cell = "";
        int inQuotes = 0;
        int i = 0;
        while (i < text.length()) {
            int c = text.charAt(i);
            if (inQuotes != 0) {
                if (c == 34) {
                    if (i + 1 < text.length() && text.charAt(i + 1) == 34) {
                        cell = cell + "\"";
                        i = i + 1;
                    } else {
                        inQuotes = 0;
                    }
                } else {
                    cell = cell + WebUtil.byteChar(c);
                }
            } else {
                if (c == 34) {
                    inQuotes = 1;
                } else {
                    if (c == 44) {
                        cur.add(cell);
                        cell = "";
                    } else {
                        if (c == 10) {
                            cur.add(cell);
                            cell = "";
                            if (isCommentRow(cur) == 0 && cur.size() > 0) {
                                CsvRow wrap = new CsvRow();
                wrap.cells = cur;
                rows.add(wrap);
                            }
                            cur = new ArrayList<Object>();
                        } else {
                            if (c != 13) {
                                cell = cell + WebUtil.byteChar(c);
                            }
                        }
                    }
                }
            }
            i = i + 1;
        }
        if (cell.length() > 0 || cur.size() > 0) {
            cur.add(cell);
            if (isCommentRow(cur) == 0) {
                CsvRow wrap = new CsvRow();
                wrap.cells = cur;
                rows.add(wrap);
            }
        }
        return rows;
    }

    static int isCommentRow(ArrayList<Object> row) {
        if (row == null || row.size() == 0) {
            return 1;
        }
        String c0 = (String) row.get(0);
        if (c0 == null) {
            return 1;
        }
        if (c0.length() == 0 && row.size() == 1) {
            return 1;
        }
        if (c0.length() > 0 && c0.charAt(0) == 35) {
            return 1;
        }
        return 0;
    }

    static String previewPartnersCsv(long companyId, String csvText) {
        return previewEntity(companyId, "PARTNER", csvText);
    }

    static String previewItemsCsv(long companyId, String csvText) {
        return previewEntity(companyId, "ITEM", csvText);
    }

    static String previewEntity(long companyId, String entityType, String csvText) {
        if (csvText == null) {
            csvText = "";
        }
        if (csvText.length() > MAX_BYTES) {
            throw new TransactionException("file too large");
        }
        ArrayList<Object> rows = parseCsv(csvText);
        int total = 0;
        int ok = 0;
        int err = 0;
        String errReport = "";
        String preview = "[";
        int ri = 0;
        while (ri < rows.size()) {
            ArrayList<Object> cells = asStringList(rows.get(ri));
            if (ri == 0) {
                ri = ri + 1;
                continue;
            }
            total = total + 1;
            if (total > MAX_ROWS) {
                err = err + 1;
                errReport = errReport + "row limit\n";
                ri = ri + 1;
                continue;
            }
            String lineErr = validateRow(companyId, entityType, cells, total);
            if (lineErr.length() > 0) {
                err = err + 1;
                errReport = errReport + lineErr + "\n";
            } else {
                ok = ok + 1;
                if (ok <= 20) {
                    if (preview.length() > 1) {
                        preview = preview + ",";
                    }
                    preview = preview + rowJson(cells);
                }
            }
            ri = ri + 1;
        }
        preview = preview + "]";
        return "{\"entityType\":" + ErpJson.str(entityType)
                + ",\"totalRows\":" + CryptoParse.longToString(total)
                + ",\"okRows\":" + CryptoParse.longToString(ok)
                + ",\"errorRows\":" + CryptoParse.longToString(err)
                + ",\"errors\":" + ErpJson.str(errReport)
                + ",\"preview\":" + preview + "}";
    }

    static String rowJson(ArrayList<Object> cells) {
        String out = "[";
        int i = 0;
        while (i < cells.size()) {
            if (i > 0) {
                out = out + ",";
            }
            out = out + ErpJson.str((String) cells.get(i));
            i = i + 1;
        }
        return out + "]";
    }

    static String validateRow(long companyId, String entityType, ArrayList<Object> cells, int rowNum) {
        if (entityType.equals("PARTNER")) {
            if (cells.size() < 3) {
                return "row " + CryptoParse.longToString(rowNum) + ": need code,name,kind";
            }
            String code = (String) cells.get(0);
            String kind = (String) cells.get(2);
            if (ErpPlatformMeta.isSafeCode(code) == 0) {
                return "row " + CryptoParse.longToString(rowNum) + ": bad code";
            }
            if (kind.equals(PartnerKind.CUSTOMER) == false && kind.equals(PartnerKind.SUPPLIER) == false
                    && kind.equals(PartnerKind.BOTH) == false) {
                return "row " + CryptoParse.longToString(rowNum) + ": bad kind";
            }
            ArrayList<Object> chk = new ArrayList<Object>();
            chk.add(CryptoParse.longToString(companyId));
            chk.add(code);
            Object existing = ErpDb.queryOne("SELECT id FROM business_partners WHERE company_id=? AND code=?", chk,
                    new LongMapper());
            if (existing != null) {
                return "row " + CryptoParse.longToString(rowNum) + ": duplicate code (skip, no overwrite)";
            }
            return "";
        }
        if (cells.size() < 2) {
            return "row " + CryptoParse.longToString(rowNum) + ": need sku,name";
        }
        String sku = (String) cells.get(0);
        if (ErpPlatformMeta.isSafeCode(sku) == 0) {
            return "row " + CryptoParse.longToString(rowNum) + ": bad sku";
        }
        ArrayList<Object> chk = new ArrayList<Object>();
        chk.add(CryptoParse.longToString(companyId));
        chk.add(sku);
        Object existing = ErpDb.queryOne("SELECT id FROM items WHERE company_id=? AND sku=?", chk, new LongMapper());
        if (existing != null) {
            return "row " + CryptoParse.longToString(rowNum) + ": duplicate sku (skip, no overwrite)";
        }
        return "";
    }

    static long importPartnersCsv(long companyId, long userId, String jobCode, String csvText) {
        return importEntity(companyId, userId, jobCode, "PARTNER", csvText);
    }

    static long importItemsCsv(long companyId, long userId, String jobCode, String csvText) {
        return importEntity(companyId, userId, jobCode, "ITEM", csvText);
    }

    static long importEntity(long companyId, long userId, String jobCode, String entityType, String csvText) {
        if (ErpPlatformAuth.hasPermission(userId, companyId, "ADMIN.IMPORT.RUN") == 0) {
            throw new TransactionException("FORBIDDEN:ADMIN.IMPORT.RUN");
        }
        if (csvText == null) {
            csvText = "";
        }
        if (csvText.length() > MAX_BYTES) {
            throw new TransactionException("file too large");
        }
        CsvImportWork work = new CsvImportWork();
        work.companyId = companyId;
        work.userId = userId;
        work.jobCode = jobCode;
        work.entityType = entityType;
        work.csvText = csvText;
        PostResultDto r = ErpPosting.runPostingWork(work);
        if (r.ok == 0) {
            throw new TransactionException(r.message);
        }
        return ErpNum.parseLong(r.documentJson);
    }

    /* legacy helpers kept for callers */
    static ArrayList<Object> splitLines(String text) {
        ArrayList<Object> rows = parseCsv(text);
        ArrayList<Object> out = new ArrayList<Object>();
        int i = 0;
        while (i < rows.size()) {
            out.add(csvLine(asStringList(rows.get(i))));
            i = i + 1;
        }
        return out;
    }

    static ArrayList<Object> asStringList(Object o) {
        ArrayList<Object> out = new ArrayList<Object>();
        if (o == null) {
            return out;
        }
        CsvRow row = (CsvRow) o;
        if (row.cells == null) {
            return out;
        }
        int i = 0;
        while (i < row.cells.size()) {
            out.add(row.cells.get(i));
            i = i + 1;
        }
        return out;
    }

    static ArrayList<Object> splitCsvLine(String line) {
        ArrayList<Object> rows = parseCsv(line + "\n");
        if (rows.size() == 0) {
            return new ArrayList<Object>();
        }
        return asStringList(rows.get(0));
    }
}

class CsvImportWork implements DbTransactionWork {
    long companyId;
    long userId;
    String jobCode;
    String entityType;
    String csvText;

    public JnFuture run(DbConnection conn) {
        long now = AsyncClockNative.wallMillis();
        ErpDb.execConn(conn,
                "INSERT INTO import_batches(company_id, batch_code, entity_type, status, total_rows, ok_rows, error_rows, error_report, created_by, created_at) VALUES ("
                        + CryptoParse.longToString(companyId) + ",'"
                        + ErpPlatformMeta.escapeLite(jobCode) + "','"
                        + entityType + "','RUNNING',0,0,0,'',"
                        + CryptoParse.longToString(userId) + ","
                        + CryptoParse.longToString(now) + ")",
                new ArrayList<Object>());
        ArrayList<Object> rows = ErpPlatformIo.parseCsv(csvText);
        int total = 0;
        int ok = 0;
        int err = 0;
        String errReport = "";
        int ri = 0;
        while (ri < rows.size()) {
            ArrayList<Object> cells = asStringList(rows.get(ri));
            if (ri == 0) {
                ri = ri + 1;
                continue;
            }
            total = total + 1;
            if (total > ErpPlatformIo.MAX_ROWS) {
                err = err + 1;
                errReport = errReport + "row limit\n";
                ri = ri + 1;
                continue;
            }
            String lineErr = ErpPlatformIo.validateRow(companyId, entityType, cells, total);
            if (lineErr.length() > 0) {
                err = err + 1;
                errReport = errReport + lineErr + "\n";
                ri = ri + 1;
                continue;
            }
            try {
                if (entityType.equals("PARTNER")) {
                    String code = (String) cells.get(0);
                    String name = (String) cells.get(1);
                    String kind = (String) cells.get(2);
                    ErpDb.execConn(conn,
                            "INSERT INTO business_partners(company_id, code, name, kind, created_at) VALUES ("
                                    + CryptoParse.longToString(companyId) + ",'"
                                    + ErpPlatformMeta.escapeLite(code) + "','"
                                    + ErpPlatformMeta.escapeLite(name) + "','" + kind + "',"
                                    + CryptoParse.longToString(now) + ")",
                            new ArrayList<Object>());
                } else {
                    String sku = (String) cells.get(0);
                    String name = (String) cells.get(1);
                    long price = 0L;
                    long cost = 0L;
                    if (cells.size() > 2) {
                        price = ErpNum.parseLong((String) cells.get(2));
                    }
                    if (cells.size() > 3) {
                        cost = ErpNum.parseLong((String) cells.get(3));
                    }
                    ErpDb.execConn(conn,
                            "INSERT INTO items(company_id, sku, name, unit_price_cents, unit_cost_amount, created_at) VALUES ("
                                    + CryptoParse.longToString(companyId) + ",'"
                                    + ErpPlatformMeta.escapeLite(sku) + "','"
                                    + ErpPlatformMeta.escapeLite(name) + "',"
                                    + CryptoParse.longToString(price) + ","
                                    + CryptoParse.longToString(cost) + ","
                                    + CryptoParse.longToString(now) + ")",
                            new ArrayList<Object>());
                }
                ok = ok + 1;
            } catch (Exception ex) {
                err = err + 1;
                errReport = errReport + "row " + CryptoParse.longToString(total) + ": insert failed\n";
            }
            ri = ri + 1;
        }
        /* On any row error after preview confirmation policy: still commit ok rows in one txn.
           Plan: 确认后单事务写入 — if we want all-or-nothing when err>0, abort. */
        if (err > 0 && ok == 0) {
            throw new TransactionException("import failed: " + errReport);
        }
        ErpDb.execConn(conn,
                "UPDATE import_batches SET status='DONE', total_rows=" + CryptoParse.longToString(total)
                        + ", ok_rows=" + CryptoParse.longToString(ok)
                        + ", error_rows=" + CryptoParse.longToString(err)
                        + ", error_report='" + ErpPlatformMeta.escapeLite(errReport)
                        + "' WHERE company_id=" + CryptoParse.longToString(companyId)
                        + " AND batch_code='" + ErpPlatformMeta.escapeLite(jobCode) + "'",
                new ArrayList<Object>());
        /* also mirror legacy import_jobs if present */
        try {
            ErpDb.execConn(conn,
                    "INSERT INTO import_jobs(company_id, job_code, entity_type, status, total_rows, ok_rows, error_rows, error_report, created_by, created_at) VALUES ("
                            + CryptoParse.longToString(companyId) + ",'"
                            + ErpPlatformMeta.escapeLite(jobCode) + "','"
                            + entityType + "','DONE',"
                            + CryptoParse.longToString(total) + ","
                            + CryptoParse.longToString(ok) + ","
                            + CryptoParse.longToString(err) + ",'"
                            + ErpPlatformMeta.escapeLite(errReport) + "',"
                            + CryptoParse.longToString(userId) + ","
                            + CryptoParse.longToString(now) + ")",
                    new ArrayList<Object>());
        } catch (Exception ignored) {
        }
        ErpDb.execConn(conn,
                "INSERT INTO audit_logs(company_id, user_id, action, entity_type, entity_id, detail, created_at) VALUES ("
                        + CryptoParse.longToString(companyId) + ","
                        + CryptoParse.longToString(userId)
                        + ",'IMPORT','" + entityType + "',0,'"
                        + ErpPlatformMeta.escapeLite(jobCode + " ok=" + CryptoParse.longToString(ok)
                                + " err=" + CryptoParse.longToString(err))
                        + "'," + CryptoParse.longToString(now) + ")",
                new ArrayList<Object>());
        ArrayList<Object> jp = new ArrayList<Object>();
        jp.add(CryptoParse.longToString(companyId));
        jp.add(jobCode);
        Object id = ErpDb.queryOneConn(conn,
                "SELECT id FROM import_batches WHERE company_id=? AND batch_code=?", jp, new LongMapper());
        PostResultDto okR = new PostResultDto();
        okR.ok = 1;
        okR.code = "OK";
        okR.message = "imported";
        okR.documentJson = CryptoParse.longToString(ErpNum.asLong(id));
        return JnFuture.completed(Erp.loop, okR);
    }
}
