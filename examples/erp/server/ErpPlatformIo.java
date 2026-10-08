/**
 * CSV import/export with formula-injection hardening. XLSX not claimed.
 */
class ErpPlatformIo {
    static int MAX_ROWS = 10000;
    static int MAX_CELL = 1024;

    /** headers + one data row (cells are String). Multi-row callers append lines. */
    static String exportCsvSafe(ArrayList<Object> headers, ArrayList<Object> cells) {
        String out = csvLine(headers);
        out = out + "\n";
        if (cells != null && cells.size() > 0) {
            out = out + csvLine(cells) + "\n";
        }
        return out;
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
        /* Excel formula injection hardening */
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

    static long importPartnersCsv(long companyId, long userId, String jobCode, String csvText) {
        if (ErpPlatformAuth.hasPermission(userId, companyId, "ADMIN.IMPORT.RUN") == 0) {
            throw new TransactionException("FORBIDDEN:ADMIN.IMPORT.RUN");
        }
        if (csvText == null) {
            csvText = "";
        }
        if (csvText.length() > 2000000) {
            throw new TransactionException("file too large");
        }
        long now = AsyncClockNative.wallMillis();
        ErpDb.exec(
                "INSERT INTO import_jobs(company_id, job_code, entity_type, status, total_rows, ok_rows, error_rows, error_report, created_by, created_at) VALUES ("
                        + CryptoParse.longToString(companyId) + ",'" + jobCode
                        + "','PARTNER','RUNNING',0,0,0,'',"
                        + CryptoParse.longToString(userId) + ","
                        + CryptoParse.longToString(now) + ")",
                new ArrayList<Object>());
        int total = 0;
        int ok = 0;
        int err = 0;
        String errReport = "";
        ArrayList<Object> lines = splitLines(csvText);
        int li = 0;
        while (li < lines.size()) {
            String line = (String) lines.get(li);
            if (li == 0) {
                li = li + 1;
                continue;
            }
            if (line.length() == 0) {
                li = li + 1;
                continue;
            }
            total = total + 1;
            if (total > MAX_ROWS) {
                err = err + 1;
                errReport = errReport + "row limit\n";
                li = li + 1;
                continue;
            }
            ArrayList<Object> cells = splitCsvLine(line);
            if (cells.size() < 3) {
                err = err + 1;
                errReport = errReport + "row " + CryptoParse.longToString(total) + ": need code,name,kind\n";
                li = li + 1;
                continue;
            }
            String code = (String) cells.get(0);
            String name = (String) cells.get(1);
            String kind = (String) cells.get(2);
            if (ErpPlatformMeta.isSafeCode(code) == 0) {
                err = err + 1;
                errReport = errReport + "row " + CryptoParse.longToString(total) + ": bad code\n";
                li = li + 1;
                continue;
            }
            if (kind.equals(PartnerKind.CUSTOMER) == false && kind.equals(PartnerKind.SUPPLIER) == false
                    && kind.equals(PartnerKind.BOTH) == false) {
                err = err + 1;
                errReport = errReport + "row " + CryptoParse.longToString(total) + ": bad kind\n";
                li = li + 1;
                continue;
            }
            try {
                ErpDb.exec(
                        "INSERT INTO business_partners(company_id, code, name, kind, created_at) VALUES ("
                                + CryptoParse.longToString(companyId) + ",'"
                                + ErpPlatformMeta.escapeLite(code) + "','"
                                + ErpPlatformMeta.escapeLite(name) + "','" + kind + "',"
                                + CryptoParse.longToString(now) + ")",
                        new ArrayList<Object>());
                ok = ok + 1;
            } catch (Exception ex) {
                err = err + 1;
                errReport = errReport + "row " + CryptoParse.longToString(total) + ": insert failed\n";
            }
            li = li + 1;
        }
        ErpDb.exec(
                "UPDATE import_jobs SET status='DONE', total_rows=" + CryptoParse.longToString(total)
                        + ", ok_rows=" + CryptoParse.longToString(ok)
                        + ", error_rows=" + CryptoParse.longToString(err)
                        + ", error_report='" + ErpPlatformMeta.escapeLite(errReport)
                        + "' WHERE company_id=" + CryptoParse.longToString(companyId)
                        + " AND job_code='" + jobCode + "'",
                new ArrayList<Object>());
        ArrayList<Object> jp = new ArrayList<Object>();
        jp.add(CryptoParse.longToString(companyId));
        jp.add(jobCode);
        Object id = ErpDb.queryOne(
                "SELECT id FROM import_jobs WHERE company_id=? AND job_code=?", jp, new LongMapper());
        return ErpNum.asLong(id);
    }

    static ArrayList<Object> splitLines(String text) {
        ArrayList<Object> out = new ArrayList<Object>();
        String buf = "";
        int i = 0;
        while (i < text.length()) {
            int c = text.charAt(i);
            if (c == 10) {
                out.add(buf);
                buf = "";
            } else {
                if (c != 13) {
                    buf = buf + WebUtil.byteChar(c);
                }
            }
            i = i + 1;
        }
        if (buf.length() > 0) {
            out.add(buf);
        }
        return out;
    }

    static ArrayList<Object> splitCsvLine(String line) {
        ArrayList<Object> out = new ArrayList<Object>();
        String buf = "";
        int i = 0;
        while (i < line.length()) {
            int c = line.charAt(i);
            if (c == 44) {
                out.add(buf);
                buf = "";
            } else {
                buf = buf + WebUtil.byteChar(c);
            }
            i = i + 1;
        }
        out.add(buf);
        return out;
    }
}
