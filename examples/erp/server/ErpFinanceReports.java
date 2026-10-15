/**
 * Trial balance, GL inquiry, AR/AP ageing.
 * @author 张晓达
 */
class ErpFinanceReports {
    static ArrayList<Object> chartOfAccounts(long companyId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult r = ErpDb.query(
                "SELECT id, account_code, account_name, account_type, COALESCE(parent_account_id,0), posting_allowed, active FROM gl_accounts WHERE company_id=? ORDER BY account_code",
                p);
        ArrayList<Object> out = new ArrayList<Object>();
        int i = 0;
        while (i < r.rows.size()) {
            Row row = r.rows.get(i);
            GlAccountDto d = new GlAccountDto();
            d.id = row.getLong(0);
            d.accountCode = row.getString(1);
            d.accountName = row.getString(2);
            d.accountType = row.getString(3);
            d.parentAccountId = row.getLong(4);
            d.postingAllowed = row.getInt(5);
            d.active = row.getInt(6);
            out.add(d);
            i = i + 1;
        }
        return out;
    }

    static ArrayList<Object> listJournals(long companyId, long fromDate, long toDate) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(CryptoParse.longToString(fromDate));
        p.add(CryptoParse.longToString(toDate));
        DbQueryResult r = ErpDb.query(
                "SELECT id, journal_number, posting_date, source_type, source_id, status, COALESCE(description,'') FROM journal_entries WHERE company_id=? AND posting_date>=? AND posting_date<=? ORDER BY posting_date, id",
                p);
        ArrayList<Object> out = new ArrayList<Object>();
        if (r == null || r.rows == null) {
            return out;
        }
        int i = 0;
        while (i < r.rows.size()) {
            Row row = r.rows.get(i);
            JournalEntryDto d = new JournalEntryDto();
            d.id = row.getLong(0);
            d.journalNumber = row.getString(1);
            if (d.journalNumber == null) {
                d.journalNumber = "";
            }
            d.postingDate = row.getLong(2);
            d.sourceType = row.getString(3);
            if (d.sourceType == null) {
                d.sourceType = "";
            }
            d.sourceId = row.getLong(4);
            d.status = row.getString(5);
            if (d.status == null) {
                d.status = "";
            }
            d.description = row.getString(6);
            if (d.description == null) {
                d.description = "";
            }
            d.lines = journalLines(companyId, d.id);
            long td = 0L;
            long tc = 0L;
            int li = 0;
            while (li < d.lines.size()) {
                JournalLineDto ln = (JournalLineDto) d.lines.get(li);
                td = td + ln.debitAmount;
                tc = tc + ln.creditAmount;
                li = li + 1;
            }
            d.totalDebit = td;
            d.totalCredit = tc;
            out.add(d);
            i = i + 1;
        }
        return out;
    }

    static ArrayList<Object> journalLines(long companyId, long journalId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(CryptoParse.longToString(journalId));
        DbQueryResult r = ErpDb.query(
                "SELECT l.account_id, a.account_code, l.debit_amount, l.credit_amount, COALESCE(l.business_partner_id,0), l.description FROM journal_entry_lines l JOIN gl_accounts a ON a.id=l.account_id WHERE l.company_id=? AND l.journal_entry_id=? ORDER BY l.line_no",
                p);
        ArrayList<Object> out = new ArrayList<Object>();
        int i = 0;
        while (i < r.rows.size()) {
            Row row = r.rows.get(i);
            JournalLineDto ln = new JournalLineDto();
            ln.accountId = row.getLong(0);
            ln.accountCode = row.getString(1);
            ln.debitAmount = row.getLong(2);
            ln.creditAmount = row.getLong(3);
            ln.businessPartnerId = row.getLong(4);
            ln.description = row.getString(5);
            out.add(ln);
            i = i + 1;
        }
        return out;
    }

    static ArrayList<Object> generalLedger(long companyId, long accountId, long fromDate, long toDate,
            long partnerId) {
        String sql = "SELECT a.account_code, l.debit_amount, l.credit_amount, COALESCE(l.business_partner_id,0), l.description, e.posting_date, e.journal_number FROM journal_entry_lines l JOIN journal_entries e ON e.id=l.journal_entry_id JOIN gl_accounts a ON a.id=l.account_id WHERE l.company_id="
                + CryptoParse.longToString(companyId)
                + " AND e.status='" + JournalStatus.POSTED + "'"
                + " AND e.posting_date>=" + CryptoParse.longToString(fromDate)
                + " AND e.posting_date<=" + CryptoParse.longToString(toDate);
        if (accountId > 0L) {
            sql = sql + " AND l.account_id=" + CryptoParse.longToString(accountId);
        }
        if (partnerId > 0L) {
            sql = sql + " AND l.business_partner_id=" + CryptoParse.longToString(partnerId);
        }
        sql = sql + " ORDER BY e.posting_date, e.id, l.line_no";
        DbQueryResult r = ErpDb.query(sql, new ArrayList<Object>());
        ArrayList<Object> out = new ArrayList<Object>();
        int i = 0;
        while (i < r.rows.size()) {
            Row row = r.rows.get(i);
            JournalLineDto ln = new JournalLineDto();
            ln.accountCode = row.getString(0);
            ln.debitAmount = row.getLong(1);
            ln.creditAmount = row.getLong(2);
            ln.businessPartnerId = row.getLong(3);
            String d4 = row.getString(4);
            String d6 = row.getString(6);
            if (d4 == null) {
                d4 = "";
            }
            if (d6 == null) {
                d6 = "";
            }
            ln.description = d4 + " [" + d6 + "]";
            out.add(ln);
            i = i + 1;
        }
        return out;
    }

    static ArrayList<Object> trialBalance(long companyId, long fromDate, long toDate) {
        ArrayList<Object> accounts = chartOfAccounts(companyId);
        ArrayList<Object> out = new ArrayList<Object>();
        if (accounts == null) {
            return out;
        }
        int i = 0;
        while (i < accounts.size()) {
            GlAccountDto a = (GlAccountDto) accounts.get(i);
            long openDr = sumSide(companyId, a.id, 0L, fromDate - 1L, 1);
            long openCr = sumSide(companyId, a.id, 0L, fromDate - 1L, 0);
            long perDr = sumSide(companyId, a.id, fromDate, toDate, 1);
            long perCr = sumSide(companyId, a.id, fromDate, toDate, 0);
            long netOpen = openDr - openCr;
            long openingDebit = 0L;
            long openingCredit = 0L;
            if (netOpen >= 0L) {
                openingDebit = netOpen;
            } else {
                openingCredit = 0L - netOpen;
            }
            long closingNet = openingDebit - openingCredit + perDr - perCr;
            long closingDebit = 0L;
            long closingCredit = 0L;
            if (closingNet >= 0L) {
                closingDebit = closingNet;
            } else {
                closingCredit = 0L - closingNet;
            }
            if (openingDebit != 0L || openingCredit != 0L || perDr != 0L || perCr != 0L || closingDebit != 0L
                    || closingCredit != 0L) {
                TrialBalanceRowDto row = new TrialBalanceRowDto();
                row.accountCode = a.accountCode;
                row.accountName = a.accountName;
                row.openingDebit = openingDebit;
                row.openingCredit = openingCredit;
                row.periodDebit = perDr;
                row.periodCredit = perCr;
                row.closingDebit = closingDebit;
                row.closingCredit = closingCredit;
                out.add(row);
            }
            i = i + 1;
        }
        return out;
    }

    static long sumSide(long companyId, long accountId, long fromDate, long toDate, int debitSide) {
        String col = "credit_amount";
        if (debitSide != 0) {
            col = "debit_amount";
        }
        String sql = "SELECT SUM(l." + col + ") FROM journal_entry_lines l, journal_entries e WHERE l.journal_entry_id=e.id AND l.company_id="
                + CryptoParse.longToString(companyId)
                + " AND l.account_id=" + CryptoParse.longToString(accountId)
                + " AND e.status='POSTED'"
                + " AND e.posting_date>=" + CryptoParse.longToString(fromDate)
                + " AND e.posting_date<=" + CryptoParse.longToString(toDate);
        Object v = ErpDb.queryOne(sql, new ArrayList<Object>(), new LongMapper());
        return ErpNum.asLong(v);
    }

    static long trialTotalsBalanced(ArrayList<Object> rows) {
        long d = 0L;
        long c = 0L;
        int i = 0;
        while (i < rows.size()) {
            TrialBalanceRowDto r = (TrialBalanceRowDto) rows.get(i);
            d = d + r.closingDebit;
            c = c + r.closingCredit;
            i = i + 1;
        }
        if (d == c) {
            return d;
        }
        return -1L;
    }

    static ArrayList<Object> arAgeing(long companyId, long asOf) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult r = ErpDb.query(
                "SELECT i.customer_id, b.name, i.due_date, i.open_amount FROM ar_invoices i JOIN business_partners b ON b.id=i.customer_id WHERE i.company_id=? AND i.open_amount>0",
                p);
        return buildAgeing(r, asOf);
    }

    static ArrayList<Object> apAgeing(long companyId, long asOf) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult r = ErpDb.query(
                "SELECT i.supplier_id, b.name, i.due_date, i.open_amount FROM ap_invoices i JOIN business_partners b ON b.id=i.supplier_id WHERE i.company_id=? AND i.open_amount>0",
                p);
        return buildAgeing(r, asOf);
    }

    static ArrayList<Object> buildAgeing(DbQueryResult r, long asOf) {
        ArrayList<Object> buckets = new ArrayList<Object>();
        int i = 0;
        while (i < r.rows.size()) {
            Row row = r.rows.get(i);
            long partnerId = row.getLong(0);
            String name = row.getString(1);
            long due = row.getLong(2);
            long open = row.getLong(3);
            AgeingBucketDto b = findOrCreate(buckets, partnerId, name);
            long days = (asOf - due) / 86400000L;
            if (days <= 0L) {
                b.currentAmount = b.currentAmount + open;
            } else {
                if (days <= 30L) {
                    b.d1to30 = b.d1to30 + open;
                } else {
                    if (days <= 60L) {
                        b.d31to60 = b.d31to60 + open;
                    } else {
                        if (days <= 90L) {
                            b.d61to90 = b.d61to90 + open;
                        } else {
                            b.d90plus = b.d90plus + open;
                        }
                    }
                }
            }
            b.totalOpen = b.totalOpen + open;
            i = i + 1;
        }
        return buckets;
    }

    static AgeingBucketDto findOrCreate(ArrayList<Object> buckets, long partnerId, String name) {
        int i = 0;
        while (i < buckets.size()) {
            AgeingBucketDto b = (AgeingBucketDto) buckets.get(i);
            if (b.partnerId == partnerId) {
                return b;
            }
            i = i + 1;
        }
        AgeingBucketDto n = new AgeingBucketDto();
        n.partnerId = partnerId;
        n.partnerName = name;
        buckets.add(n);
        return n;
    }

    static ArrayList<Object> listBankAccounts(long companyId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult r = ErpDb.query(
                "SELECT id, account_name, currency, ledger_account_id, active, balance FROM bank_accounts WHERE company_id=? ORDER BY id",
                p);
        ArrayList<Object> out = new ArrayList<Object>();
        int i = 0;
        while (i < r.rows.size()) {
            Row row = r.rows.get(i);
            long bal = 0L;
            if (row.columnCount() > 5) {
                bal = row.getLong(5);
            }
            String json = "{\"id\":" + ErpJson.num(row.getLong(0))
                    + ",\"accountName\":" + ErpJson.str(row.getString(1))
                    + ",\"currency\":" + ErpJson.str(row.getString(2))
                    + ",\"ledgerAccountId\":" + ErpJson.num(row.getLong(3))
                    + ",\"active\":" + String.valueOf(row.getInt(4))
                    + ",\"balance\":" + ErpJson.num(bal)
                    + ",\"code\":" + ErpJson.str(row.getString(1))
                    + ",\"name\":" + ErpJson.str(row.getString(1)) + "}";
            out.add(json);
            i = i + 1;
        }
        return out;
    }
}
