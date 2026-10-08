/**
 * AR / AP invoices and payment allocation (integer money, company-scoped).
 */
class ErpFinanceArAp {
    static ArInvoiceDto createArInvoice(long companyId, long customerId, long totalAmount, long invoiceDate,
            long dueDate, long sourceDeliveryId) {
        if (totalAmount <= 0L) {
            return null;
        }
        try {
            long now = AsyncClockNative.wallMillis();
            long seq = ErpDb.nextDocNo(companyId, "AR");
            String no = "AR-" + CryptoParse.longToString(seq);
            String ins = "INSERT INTO ar_invoices(company_id, customer_id, invoice_number, invoice_date, due_date, currency, total_amount, paid_amount, open_amount, status, source_delivery_id, created_at) VALUES ("
                    + CryptoParse.longToString(companyId) + ","
                    + CryptoParse.longToString(customerId) + ",'"
                    + no + "',"
                    + CryptoParse.longToString(invoiceDate) + ","
                    + CryptoParse.longToString(dueDate) + ",'USD',"
                    + CryptoParse.longToString(totalAmount) + ",0,"
                    + CryptoParse.longToString(totalAmount) + ",'"
                    + InvoiceStatus.DRAFT + "',"
                    + CryptoParse.longToString(sourceDeliveryId) + ","
                    + CryptoParse.longToString(now) + ")";
            ErpDb.exec(ins, new ArrayList<Object>());
            ArrayList<Object> qp = new ArrayList<Object>();
            qp.add(CryptoParse.longToString(companyId));
            qp.add(no);
            Object idObj = ErpDb.queryOne(
                    "SELECT id FROM ar_invoices WHERE company_id=? AND invoice_number=?", qp, new LongMapper());
            ArInvoiceDto d = new ArInvoiceDto();
            d.id = ErpNum.asLong(idObj);
            d.customerId = customerId;
            d.invoiceNumber = no;
            d.invoiceDate = invoiceDate;
            d.dueDate = dueDate;
            d.totalAmount = totalAmount;
            d.paidAmount = 0L;
            d.openAmount = totalAmount;
            d.status = InvoiceStatus.DRAFT;
            return d;
        } catch (Exception ex) {
            System.out.println("AR_CREATE_EX");
            if (ex.getMessage() != null) {
                System.out.println(ex.getMessage());
            }
            return null;
        }
    }

    static PostResultDto postArInvoice(long companyId, long userId, long invoiceId) {
        ArInvoicePostWork work = new ArInvoicePostWork();
        work.companyId = companyId;
        work.userId = userId;
        work.invoiceId = invoiceId;
        return runPostWork(work);
    }

    static PostResultDto postIncomingPayment(long companyId, long userId, IncomingPaymentRequest req) {
        if (req == null || req.idempotencyKey == null || req.idempotencyKey.length() == 0) {
            PostResultDto bad = new PostResultDto();
            bad.ok = 0;
            bad.code = "IDEMPOTENCY_REQUIRED";
            bad.message = "Idempotency-Key required";
            return bad;
        }
        IncomingPaymentWork work = new IncomingPaymentWork();
        work.companyId = companyId;
        work.userId = userId;
        work.req = req;
        return runPostWork(work);
    }

    static ApInvoiceDto createApInvoice(long companyId, long supplierId, long totalAmount, long invoiceDate,
            long dueDate, long sourceGrId) {
        if (totalAmount <= 0L) {
            return null;
        }
        try {
            long now = AsyncClockNative.wallMillis();
            long seq = ErpDb.nextDocNo(companyId, "AP");
            String no = "AP-" + CryptoParse.longToString(seq);
            String ins = "INSERT INTO ap_invoices(company_id, supplier_id, invoice_number, invoice_date, due_date, currency, total_amount, paid_amount, open_amount, status, source_gr_id, created_at) VALUES ("
                    + CryptoParse.longToString(companyId) + ","
                    + CryptoParse.longToString(supplierId) + ",'"
                    + no + "',"
                    + CryptoParse.longToString(invoiceDate) + ","
                    + CryptoParse.longToString(dueDate) + ",'USD',"
                    + CryptoParse.longToString(totalAmount) + ",0,"
                    + CryptoParse.longToString(totalAmount) + ",'"
                    + InvoiceStatus.DRAFT + "',"
                    + CryptoParse.longToString(sourceGrId) + ","
                    + CryptoParse.longToString(now) + ")";
            ErpDb.exec(ins, new ArrayList<Object>());
            ArrayList<Object> qp = new ArrayList<Object>();
            qp.add(CryptoParse.longToString(companyId));
            qp.add(no);
            Object idObj = ErpDb.queryOne(
                    "SELECT id FROM ap_invoices WHERE company_id=? AND invoice_number=?", qp, new LongMapper());
            ApInvoiceDto d = new ApInvoiceDto();
            d.id = ErpNum.asLong(idObj);
            d.supplierId = supplierId;
            d.invoiceNumber = no;
            d.invoiceDate = invoiceDate;
            d.dueDate = dueDate;
            d.totalAmount = totalAmount;
            d.paidAmount = 0L;
            d.openAmount = totalAmount;
            d.status = InvoiceStatus.DRAFT;
            return d;
        } catch (Exception ex) {
            System.out.println("AP_CREATE_EX");
            if (ex.getMessage() != null) {
                System.out.println(ex.getMessage());
            }
            return null;
        }
    }

    static PostResultDto postApInvoice(long companyId, long userId, long invoiceId) {
        ApInvoicePostWork work = new ApInvoicePostWork();
        work.companyId = companyId;
        work.userId = userId;
        work.invoiceId = invoiceId;
        return runPostWork(work);
    }

    static PostResultDto postOutgoingPayment(long companyId, long userId, OutgoingPaymentRequest req) {
        if (req == null || req.idempotencyKey == null || req.idempotencyKey.length() == 0) {
            PostResultDto bad = new PostResultDto();
            bad.ok = 0;
            bad.code = "IDEMPOTENCY_REQUIRED";
            bad.message = "Idempotency-Key required";
            return bad;
        }
        OutgoingPaymentWork work = new OutgoingPaymentWork();
        work.companyId = companyId;
        work.userId = userId;
        work.req = req;
        return runPostWork(work);
    }

    static PostResultDto runPostWork(DbTransactionWork work) {
        try {
            JnFuture f = Erp.db.transaction(work);
            FutureBlocker.blockingWait(f);
            if (f.getError() != null) {
                throw f.getError();
            }
            Object v = f.getValue();
            if (v == null) {
                PostResultDto bad = new PostResultDto();
                bad.ok = 0;
                bad.code = "POST_NULL";
                bad.message = "null";
                return bad;
            }
            return (PostResultDto) v;
        } catch (Exception ex) {
            PostResultDto bad = new PostResultDto();
            bad.ok = 0;
            bad.code = "POST_FAILED";
            if (ex.getMessage() != null) {
                bad.message = ex.getMessage();
            } else {
                bad.message = "failed";
            }
            return bad;
        }
    }

    static long bankLedgerAccountConn(DbConnection conn, long companyId, long bankAccountId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(bankAccountId));
        p.add(CryptoParse.longToString(companyId));
        Object v = ErpDb.queryOneConn(conn,
                "SELECT ledger_account_id FROM bank_accounts WHERE id=? AND company_id=? AND active=1", p,
                new LongMapper());
        return ErpNum.asLong(v);
    }

    static ArInvoiceDto mapAr(Row row) {
        ArInvoiceDto d = new ArInvoiceDto();
        d.id = row.getLong(0);
        d.customerId = row.getLong(1);
        d.invoiceNumber = row.getString(2);
        d.invoiceDate = row.getLong(3);
        d.dueDate = row.getLong(4);
        d.totalAmount = row.getLong(5);
        d.paidAmount = row.getLong(6);
        d.openAmount = row.getLong(7);
        d.status = row.getString(8);
        return d;
    }

    static ApInvoiceDto mapAp(Row row) {
        ApInvoiceDto d = new ApInvoiceDto();
        d.id = row.getLong(0);
        d.supplierId = row.getLong(1);
        d.invoiceNumber = row.getString(2);
        d.invoiceDate = row.getLong(3);
        d.dueDate = row.getLong(4);
        d.totalAmount = row.getLong(5);
        d.paidAmount = row.getLong(6);
        d.openAmount = row.getLong(7);
        d.status = row.getString(8);
        return d;
    }

    static ArrayList<Object> listAr(long companyId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult r = ErpDb.query(
                "SELECT id, customer_id, invoice_number, invoice_date, due_date, total_amount, paid_amount, open_amount, status FROM ar_invoices WHERE company_id=? ORDER BY id",
                p);
        ArrayList<Object> out = new ArrayList<Object>();
        int i = 0;
        while (i < r.rows.size()) {
            out.add(mapAr(r.rows.get(i)));
            i = i + 1;
        }
        return out;
    }

    static ArrayList<Object> listAp(long companyId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult r = ErpDb.query(
                "SELECT id, supplier_id, invoice_number, invoice_date, due_date, total_amount, paid_amount, open_amount, status FROM ap_invoices WHERE company_id=? ORDER BY id",
                p);
        ArrayList<Object> out = new ArrayList<Object>();
        int i = 0;
        while (i < r.rows.size()) {
            out.add(mapAp(r.rows.get(i)));
            i = i + 1;
        }
        return out;
    }
}

class ArInvoiceCreateWork implements DbTransactionWork {
    long companyId;
    long customerId;
    long totalAmount;
    long invoiceDate;
    long dueDate;
    long sourceDeliveryId;

    public JnFuture run(DbConnection conn) {
        long now = AsyncClockNative.wallMillis();
        long seq = ErpDb.nextDocNoConn(conn, companyId, "AR");
        String no = "AR-" + CryptoParse.longToString(seq);
        long id = ErpDb.insertReturningIdConn(conn,
                "INSERT INTO ar_invoices(company_id, customer_id, invoice_number, invoice_date, due_date, currency, total_amount, paid_amount, open_amount, status, source_delivery_id, created_at) VALUES ("
                        + CryptoParse.longToString(companyId) + ","
                        + CryptoParse.longToString(customerId) + ",'"
                        + no + "',"
                        + CryptoParse.longToString(invoiceDate) + ","
                        + CryptoParse.longToString(dueDate) + ",'USD',"
                        + CryptoParse.longToString(totalAmount) + ",0,"
                        + CryptoParse.longToString(totalAmount) + ",'"
                        + InvoiceStatus.DRAFT + "',"
                        + CryptoParse.longToString(sourceDeliveryId) + ","
                        + CryptoParse.longToString(now) + ")",
                new ArrayList<Object>());
        ArInvoiceDto d = new ArInvoiceDto();
        d.id = id;
        d.customerId = customerId;
        d.invoiceNumber = no;
        d.invoiceDate = invoiceDate;
        d.dueDate = dueDate;
        d.totalAmount = totalAmount;
        d.paidAmount = 0L;
        d.openAmount = totalAmount;
        d.status = InvoiceStatus.DRAFT;
        return JnFuture.completed(Erp.loop, d);
    }
}

class ArInvoicePostWork implements DbTransactionWork {
    long companyId;
    long userId;
    long invoiceId;

    public JnFuture run(DbConnection conn) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(invoiceId));
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult r = ErpDb.queryConn(conn,
                "SELECT id, customer_id, invoice_number, invoice_date, due_date, total_amount, paid_amount, open_amount, status FROM ar_invoices WHERE id=? AND company_id=?",
                p);
        if (r.rows.size() == 0) {
            throw new TransactionException("AR not found");
        }
        ArInvoiceDto inv = ErpFinanceArAp.mapAr(r.rows.get(0));
        if (inv.status.equals(InvoiceStatus.DRAFT) == false) {
            PostResultDto replay = new PostResultDto();
            replay.ok = 1;
            replay.code = "ALREADY_POSTED";
            replay.message = "already posted";
            replay.documentJson = "{\"id\":" + ErpJson.num(inv.id) + "}";
            return JnFuture.completed(Erp.loop, replay);
        }
        long ar = ErpFinanceCore.mappedAccountIdConn(conn, companyId, AccountMapKey.AR);
        long rev = ErpFinanceCore.mappedAccountIdConn(conn, companyId, AccountMapKey.REVENUE);
        PostingRequest req = new PostingRequest();
        req.sourceType = JournalSource.AR_INVOICE;
        req.sourceId = invoiceId;
        req.postingDate = inv.invoiceDate;
        req.documentDate = inv.invoiceDate;
        req.description = "AR " + inv.invoiceNumber;
        req.idempotencyKey = "je-ar-" + CryptoParse.longToString(invoiceId);
        req.lines = new ArrayList<Object>();
        req.lines.add(ErpFinanceCore.line(ar, inv.totalAmount, 0L, inv.customerId, "AR debit"));
        req.lines.add(ErpFinanceCore.line(rev, 0L, inv.totalAmount, inv.customerId, "Revenue"));
        PostResultDto je = ErpFinanceCore.postJournalConn(conn, companyId, userId, req);
        if (je.ok == 0) {
            return JnFuture.completed(Erp.loop, je);
        }
        ErpDb.execConn(conn,
                "UPDATE ar_invoices SET status='" + InvoiceStatus.POSTED + "' WHERE id="
                        + CryptoParse.longToString(invoiceId) + " AND company_id="
                        + CryptoParse.longToString(companyId),
                new ArrayList<Object>());
        PostResultDto ok = new PostResultDto();
        ok.ok = 1;
        ok.code = "OK";
        ok.message = "AR posted";
        ok.documentJson = "{\"id\":" + ErpJson.num(invoiceId) + ",\"journal\":" + je.documentJson + "}";
        return JnFuture.completed(Erp.loop, ok);
    }
}

class IncomingPaymentWork implements DbTransactionWork {
    long companyId;
    long userId;
    IncomingPaymentRequest req;

    public JnFuture run(DbConnection conn) {
        long now = AsyncClockNative.wallMillis();
        String cached = ErpPosting.cachedIdempotencyConn(conn, companyId, req.idempotencyKey);
        if (cached.length() > 0) {
            return JnFuture.completed(Erp.loop, ErpPosting.resultFromJson(cached));
        }
        if (req.amount <= 0L) {
            throw new TransactionException("invalid payment amount");
        }
        long bankGl = ErpFinanceArAp.bankLedgerAccountConn(conn, companyId, req.bankAccountId);
        if (bankGl == 0L) {
            throw new TransactionException("bank account invalid");
        }
        long allocTotal = 0L;
        if (req.allocations != null) {
            int ai = 0;
            while (ai < req.allocations.size()) {
                PaymentAllocationInput a = (PaymentAllocationInput) req.allocations.get(ai);
                if (a.allocatedAmount <= 0L) {
                    throw new TransactionException("allocatedAmount must be > 0");
                }
                allocTotal = allocTotal + a.allocatedAmount;
                ai = ai + 1;
            }
        }
        if (allocTotal > req.amount) {
            throw new TransactionException("allocation exceeds payment");
        }
        long seq = ErpDb.nextDocNoConn(conn, companyId, "IP");
        ArrayList<Object> hdr = new ArrayList<Object>();
        hdr.add(CryptoParse.longToString(companyId));
        hdr.add(CryptoParse.longToString(req.customerId));
        hdr.add(CryptoParse.longToString(req.paymentDate));
        hdr.add(CryptoParse.longToString(req.amount));
        hdr.add(CryptoParse.longToString(allocTotal));
        hdr.add(CryptoParse.longToString(req.bankAccountId));
        hdr.add(PaymentStatus.POSTED);
        hdr.add(req.idempotencyKey);
        hdr.add(CryptoParse.longToString(now));
        long payId = ErpDb.insertReturningIdConn(conn,
                "INSERT INTO incoming_payments(company_id, customer_id, payment_date, amount, allocated_amount, bank_account_id, status, idempotency_key, created_at) VALUES (?,?,?,?,?,?,?,?,?)",
                hdr);
        if (req.allocations != null) {
            int ai2 = 0;
            while (ai2 < req.allocations.size()) {
                PaymentAllocationInput a = (PaymentAllocationInput) req.allocations.get(ai2);
                String lockSql = "SELECT id, open_amount, paid_amount, status FROM ar_invoices WHERE id="
                        + CryptoParse.longToString(a.invoiceId) + " AND company_id="
                        + CryptoParse.longToString(companyId);
                if (Erp.dialect.equals("postgresql")) {
                    lockSql = lockSql + " FOR UPDATE";
                }
                DbQueryResult ir = ErpDb.queryConn(conn, lockSql, new ArrayList<Object>());
                if (ir.rows.size() == 0) {
                    throw new TransactionException("AR invoice not found");
                }
                Row irow = ir.rows.get(0);
                long open = irow.getLong(1);
                long paid = irow.getLong(2);
                String st = irow.getString(3);
                if (st.equals(InvoiceStatus.POSTED) == false && st.equals(InvoiceStatus.PARTIALLY_PAID) == false) {
                    throw new TransactionException("invoice not open for payment");
                }
                if (a.allocatedAmount > open) {
                    throw new TransactionException("over-allocation");
                }
                long newPaid = paid + a.allocatedAmount;
                long newOpen = open - a.allocatedAmount;
                String newSt = InvoiceStatus.PARTIALLY_PAID;
                if (newOpen == 0L) {
                    newSt = InvoiceStatus.PAID;
                }
                ErpDb.execConn(conn,
                        "UPDATE ar_invoices SET paid_amount=" + CryptoParse.longToString(newPaid)
                                + ", open_amount=" + CryptoParse.longToString(newOpen)
                                + ", status='" + newSt + "' WHERE id=" + CryptoParse.longToString(a.invoiceId)
                                + " AND company_id=" + CryptoParse.longToString(companyId)
                                + " AND open_amount=" + CryptoParse.longToString(open),
                        new ArrayList<Object>());
                ErpDb.execConn(conn,
                        "INSERT INTO incoming_payment_allocations(company_id, payment_id, invoice_id, allocated_amount) VALUES ("
                                + CryptoParse.longToString(companyId) + ","
                                + CryptoParse.longToString(payId) + ","
                                + CryptoParse.longToString(a.invoiceId) + ","
                                + CryptoParse.longToString(a.allocatedAmount) + ")",
                        new ArrayList<Object>());
                ai2 = ai2 + 1;
            }
        }
        long ar = ErpFinanceCore.mappedAccountIdConn(conn, companyId, AccountMapKey.AR);
        PostingRequest jeReq = new PostingRequest();
        jeReq.sourceType = JournalSource.INCOMING_PAYMENT;
        jeReq.sourceId = payId;
        jeReq.postingDate = req.paymentDate;
        jeReq.documentDate = req.paymentDate;
        jeReq.description = "Incoming payment";
        jeReq.idempotencyKey = "je-ip-" + CryptoParse.longToString(payId);
        jeReq.lines = new ArrayList<Object>();
        jeReq.lines.add(ErpFinanceCore.line(bankGl, req.amount, 0L, req.customerId, "Bank"));
        jeReq.lines.add(ErpFinanceCore.line(ar, 0L, req.amount, req.customerId, "AR credit"));
        PostResultDto je = ErpFinanceCore.postJournalConn(conn, companyId, userId, jeReq);
        if (je.ok == 0) {
            throw new TransactionException(je.message);
        }
        String docJson = "{\"id\":" + ErpJson.num(payId) + ",\"amount\":" + ErpJson.num(req.amount) + "}";
        PostResultDto ok = new PostResultDto();
        ok.ok = 1;
        ok.code = "OK";
        ok.message = "payment posted";
        ok.documentJson = docJson;
        ErpPosting.storeIdempotencyConn(conn, companyId, req.idempotencyKey, "IP", docJson, now);
        return JnFuture.completed(Erp.loop, ok);
    }
}

class ApInvoiceCreateWork implements DbTransactionWork {
    long companyId;
    long supplierId;
    long totalAmount;
    long invoiceDate;
    long dueDate;
    long sourceGrId;

    public JnFuture run(DbConnection conn) {
        long now = AsyncClockNative.wallMillis();
        long seq = ErpDb.nextDocNoConn(conn, companyId, "AP");
        String no = "AP-" + CryptoParse.longToString(seq);
        String ins = "INSERT INTO ap_invoices(company_id, supplier_id, invoice_number, invoice_date, due_date, currency, total_amount, paid_amount, open_amount, status, source_gr_id, created_at) VALUES ("
                + CryptoParse.longToString(companyId) + ","
                + CryptoParse.longToString(supplierId) + ",'"
                + no + "',"
                + CryptoParse.longToString(invoiceDate) + ","
                + CryptoParse.longToString(dueDate) + ",'USD',"
                + CryptoParse.longToString(totalAmount) + ",0,"
                + CryptoParse.longToString(totalAmount) + ",'"
                + InvoiceStatus.DRAFT + "',"
                + CryptoParse.longToString(sourceGrId) + ","
                + CryptoParse.longToString(now) + ")";
        long id = ErpDb.insertReturningIdConn(conn, ins, new ArrayList<Object>());
        ApInvoiceDto d = new ApInvoiceDto();
        d.id = id;
        d.supplierId = supplierId;
        d.invoiceNumber = no;
        d.invoiceDate = invoiceDate;
        d.dueDate = dueDate;
        d.totalAmount = totalAmount;
        d.paidAmount = 0L;
        d.openAmount = totalAmount;
        d.status = InvoiceStatus.DRAFT;
        return JnFuture.completed(Erp.loop, d);
    }
}

class ApInvoicePostWork implements DbTransactionWork {
    long companyId;
    long userId;
    long invoiceId;

    public JnFuture run(DbConnection conn) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(invoiceId));
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult r = ErpDb.queryConn(conn,
                "SELECT id, supplier_id, invoice_number, invoice_date, due_date, total_amount, paid_amount, open_amount, status FROM ap_invoices WHERE id=? AND company_id=?",
                p);
        if (r.rows.size() == 0) {
            throw new TransactionException("AP not found");
        }
        ApInvoiceDto inv = ErpFinanceArAp.mapAp(r.rows.get(0));
        if (inv.status.equals(InvoiceStatus.DRAFT) == false) {
            PostResultDto replay = new PostResultDto();
            replay.ok = 1;
            replay.code = "ALREADY_POSTED";
            replay.message = "already posted";
            replay.documentJson = "{\"id\":" + ErpJson.num(inv.id) + "}";
            return JnFuture.completed(Erp.loop, replay);
        }
        long ap = ErpFinanceCore.mappedAccountIdConn(conn, companyId, AccountMapKey.AP);
        long clr = ErpFinanceCore.mappedAccountIdConn(conn, companyId, AccountMapKey.GR_CLEARING);
        PostingRequest req = new PostingRequest();
        req.sourceType = JournalSource.AP_INVOICE;
        req.sourceId = invoiceId;
        req.postingDate = inv.invoiceDate;
        req.documentDate = inv.invoiceDate;
        req.description = "AP " + inv.invoiceNumber;
        req.idempotencyKey = "je-ap-" + CryptoParse.longToString(invoiceId);
        req.lines = new ArrayList<Object>();
        req.lines.add(ErpFinanceCore.line(clr, inv.totalAmount, 0L, inv.supplierId, "Clear GR/IR"));
        req.lines.add(ErpFinanceCore.line(ap, 0L, inv.totalAmount, inv.supplierId, "AP credit"));
        PostResultDto je = ErpFinanceCore.postJournalConn(conn, companyId, userId, req);
        if (je.ok == 0) {
            return JnFuture.completed(Erp.loop, je);
        }
        ErpDb.execConn(conn,
                "UPDATE ap_invoices SET status='" + InvoiceStatus.POSTED + "' WHERE id="
                        + CryptoParse.longToString(invoiceId) + " AND company_id="
                        + CryptoParse.longToString(companyId),
                new ArrayList<Object>());
        PostResultDto ok = new PostResultDto();
        ok.ok = 1;
        ok.code = "OK";
        ok.message = "AP posted";
        ok.documentJson = "{\"id\":" + ErpJson.num(invoiceId) + ",\"journal\":" + je.documentJson + "}";
        return JnFuture.completed(Erp.loop, ok);
    }
}

class OutgoingPaymentWork implements DbTransactionWork {
    long companyId;
    long userId;
    OutgoingPaymentRequest req;

    public JnFuture run(DbConnection conn) {
        long now = AsyncClockNative.wallMillis();
        String cached = ErpPosting.cachedIdempotencyConn(conn, companyId, req.idempotencyKey);
        if (cached.length() > 0) {
            return JnFuture.completed(Erp.loop, ErpPosting.resultFromJson(cached));
        }
        if (req.amount <= 0L) {
            throw new TransactionException("invalid payment amount");
        }
        long bankGl = ErpFinanceArAp.bankLedgerAccountConn(conn, companyId, req.bankAccountId);
        if (bankGl == 0L) {
            throw new TransactionException("bank account invalid");
        }
        long allocTotal = 0L;
        if (req.allocations != null) {
            int ai = 0;
            while (ai < req.allocations.size()) {
                PaymentAllocationInput a = (PaymentAllocationInput) req.allocations.get(ai);
                if (a.allocatedAmount <= 0L) {
                    throw new TransactionException("allocatedAmount must be > 0");
                }
                allocTotal = allocTotal + a.allocatedAmount;
                ai = ai + 1;
            }
        }
        if (allocTotal > req.amount) {
            throw new TransactionException("allocation exceeds payment");
        }
        long seq = ErpDb.nextDocNoConn(conn, companyId, "OP");
        ArrayList<Object> hdr = new ArrayList<Object>();
        hdr.add(CryptoParse.longToString(companyId));
        hdr.add(CryptoParse.longToString(req.supplierId));
        hdr.add(CryptoParse.longToString(req.paymentDate));
        hdr.add(CryptoParse.longToString(req.amount));
        hdr.add(CryptoParse.longToString(allocTotal));
        hdr.add(CryptoParse.longToString(req.bankAccountId));
        hdr.add(PaymentStatus.POSTED);
        hdr.add(req.idempotencyKey);
        hdr.add(CryptoParse.longToString(now));
        long payId = ErpDb.insertReturningIdConn(conn,
                "INSERT INTO outgoing_payments(company_id, supplier_id, payment_date, amount, allocated_amount, bank_account_id, status, idempotency_key, created_at) VALUES (?,?,?,?,?,?,?,?,?)",
                hdr);
        if (req.allocations != null) {
            int ai2 = 0;
            while (ai2 < req.allocations.size()) {
                PaymentAllocationInput a = (PaymentAllocationInput) req.allocations.get(ai2);
                String lockSql = "SELECT id, open_amount, paid_amount, status FROM ap_invoices WHERE id="
                        + CryptoParse.longToString(a.invoiceId) + " AND company_id="
                        + CryptoParse.longToString(companyId);
                if (Erp.dialect.equals("postgresql")) {
                    lockSql = lockSql + " FOR UPDATE";
                }
                DbQueryResult ir = ErpDb.queryConn(conn, lockSql, new ArrayList<Object>());
                if (ir.rows.size() == 0) {
                    throw new TransactionException("AP invoice not found");
                }
                Row irow = ir.rows.get(0);
                long open = irow.getLong(1);
                long paid = irow.getLong(2);
                String st = irow.getString(3);
                if (st.equals(InvoiceStatus.POSTED) == false && st.equals(InvoiceStatus.PARTIALLY_PAID) == false) {
                    throw new TransactionException("invoice not open for payment");
                }
                if (a.allocatedAmount > open) {
                    throw new TransactionException("over-allocation");
                }
                long newPaid = paid + a.allocatedAmount;
                long newOpen = open - a.allocatedAmount;
                String newSt = InvoiceStatus.PARTIALLY_PAID;
                if (newOpen == 0L) {
                    newSt = InvoiceStatus.PAID;
                }
                ErpDb.execConn(conn,
                        "UPDATE ap_invoices SET paid_amount=" + CryptoParse.longToString(newPaid)
                                + ", open_amount=" + CryptoParse.longToString(newOpen)
                                + ", status='" + newSt + "' WHERE id=" + CryptoParse.longToString(a.invoiceId)
                                + " AND company_id=" + CryptoParse.longToString(companyId)
                                + " AND open_amount=" + CryptoParse.longToString(open),
                        new ArrayList<Object>());
                ErpDb.execConn(conn,
                        "INSERT INTO outgoing_payment_allocations(company_id, payment_id, invoice_id, allocated_amount) VALUES ("
                                + CryptoParse.longToString(companyId) + ","
                                + CryptoParse.longToString(payId) + ","
                                + CryptoParse.longToString(a.invoiceId) + ","
                                + CryptoParse.longToString(a.allocatedAmount) + ")",
                        new ArrayList<Object>());
                ai2 = ai2 + 1;
            }
        }
        long ap = ErpFinanceCore.mappedAccountIdConn(conn, companyId, AccountMapKey.AP);
        PostingRequest jeReq = new PostingRequest();
        jeReq.sourceType = JournalSource.OUTGOING_PAYMENT;
        jeReq.sourceId = payId;
        jeReq.postingDate = req.paymentDate;
        jeReq.documentDate = req.paymentDate;
        jeReq.description = "Outgoing payment";
        jeReq.idempotencyKey = "je-op-" + CryptoParse.longToString(payId);
        jeReq.lines = new ArrayList<Object>();
        jeReq.lines.add(ErpFinanceCore.line(ap, req.amount, 0L, req.supplierId, "AP debit"));
        jeReq.lines.add(ErpFinanceCore.line(bankGl, 0L, req.amount, req.supplierId, "Bank"));
        PostResultDto je = ErpFinanceCore.postJournalConn(conn, companyId, userId, jeReq);
        if (je.ok == 0) {
            throw new TransactionException(je.message);
        }
        String docJson = "{\"id\":" + ErpJson.num(payId) + ",\"amount\":" + ErpJson.num(req.amount) + "}";
        PostResultDto ok = new PostResultDto();
        ok.ok = 1;
        ok.code = "OK";
        ok.message = "payment posted";
        ok.documentJson = docJson;
        ErpPosting.storeIdempotencyConn(conn, companyId, req.idempotencyKey, "OP", docJson, now);
        return JnFuture.completed(Erp.loop, ok);
    }
}
