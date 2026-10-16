/**
 * AR/AP settlement sub-ledger: receipts/payments, allocation, reverse, bank cash.
 * Integer money only. Receipt ≠ revenue; payment ≠ expense (no fake P&L).
 * @author 张晓达
 */
class ErpFinanceSettlement {

    static String settlementOf(long open, long paid, long effective) {
        if (open <= 0L) {
            return SettlementStatus.SETTLED;
        }
        if (paid > 0L && open < effective) {
            return SettlementStatus.PARTIAL;
        }
        return SettlementStatus.OPEN;
    }

    static int invoiceOpenForSettle(String confirmStatus) {
        if (confirmStatus == null) {
            return 0;
        }
        if (confirmStatus.equals(InvoiceStatus.PROVISIONAL)
                || confirmStatus.equals(InvoiceStatus.POSTED)
                || confirmStatus.equals(InvoiceStatus.PARTIALLY_PAID)
                || confirmStatus.equals(InvoiceStatus.PAID)) {
            return 1;
        }
        return 0;
    }

    /** Create open AR for settlement tests / manual entry (confirm=POSTED, no GL). */
    static long createOpenAr(long companyId, long customerId, long totalAmount, long invoiceDate, long dueDate,
            String currency) {
        if (totalAmount <= 0L) {
            return 0L;
        }
        if (currency == null || currency.length() == 0) {
            currency = "CNY";
        }
        CreateOpenArWork w = new CreateOpenArWork();
        w.companyId = companyId;
        w.customerId = customerId;
        w.totalAmount = totalAmount;
        w.invoiceDate = invoiceDate;
        w.dueDate = dueDate;
        w.currency = currency;
        PostResultDto r = ErpPosting.runPostingWork(w);
        if (r == null || r.ok == 0 || r.documentJson == null) {
            return 0L;
        }
        return parseDocId(r.documentJson);
    }

    static long createOpenAp(long companyId, long supplierId, long totalAmount, long invoiceDate, long dueDate,
            String currency) {
        if (totalAmount <= 0L) {
            return 0L;
        }
        if (currency == null || currency.length() == 0) {
            currency = "CNY";
        }
        CreateOpenApWork w = new CreateOpenApWork();
        w.companyId = companyId;
        w.supplierId = supplierId;
        w.totalAmount = totalAmount;
        w.invoiceDate = invoiceDate;
        w.dueDate = dueDate;
        w.currency = currency;
        PostResultDto r = ErpPosting.runPostingWork(w);
        if (r == null || r.ok == 0 || r.documentJson == null) {
            return 0L;
        }
        return parseDocId(r.documentJson);
    }

    static long parseDocId(String json) {
        if (json == null) {
            return 0L;
        }
        int idx = json.indexOf("\"id\":");
        if (idx < 0) {
            return 0L;
        }
        int i = idx + 5;
        String n = "";
        while (i < json.length()) {
            int c = json.charAt(i);
            if (c >= 48 && c <= 57) {
                n = n + WebUtil.byteChar(c);
            } else {
                if (n.length() > 0) {
                    break;
                }
            }
            i = i + 1;
        }
        if (n.length() == 0) {
            return 0L;
        }
        return ErpNum.parseLong(n);
    }

    static PostResultDto createReceipt(long companyId, long userId, IncomingPaymentRequest req) {
        if (req == null || req.idempotencyKey == null || req.idempotencyKey.length() == 0) {
            return bad("IDEMPOTENCY_REQUIRED", "Idempotency-Key required");
        }
        ReceiptCreateWork w = new ReceiptCreateWork();
        w.companyId = companyId;
        w.userId = userId;
        w.req = req;
        return ErpPosting.runPostingWork(w);
    }

    static PostResultDto postReceipt(long companyId, long userId, long paymentId, String idempotencyKey) {
        ReceiptPostWork w = new ReceiptPostWork();
        w.companyId = companyId;
        w.userId = userId;
        w.paymentId = paymentId;
        w.idempotencyKey = idempotencyKey;
        return ErpPosting.runPostingWork(w);
    }

    static PostResultDto allocateReceipt(long companyId, long userId, SettlementAllocateRequest req) {
        if (req == null || req.idempotencyKey == null || req.idempotencyKey.length() == 0) {
            return bad("IDEMPOTENCY_REQUIRED", "Idempotency-Key required");
        }
        ReceiptAllocWork w = new ReceiptAllocWork();
        w.companyId = companyId;
        w.userId = userId;
        w.req = req;
        return ErpPosting.runPostingWork(w);
    }

    static PostResultDto reverseReceiptAlloc(long companyId, long userId, SettlementReverseRequest req) {
        if (req == null || req.idempotencyKey == null || req.idempotencyKey.length() == 0) {
            return bad("IDEMPOTENCY_REQUIRED", "Idempotency-Key required");
        }
        ReceiptReverseWork w = new ReceiptReverseWork();
        w.companyId = companyId;
        w.userId = userId;
        w.req = req;
        return ErpPosting.runPostingWork(w);
    }

    static PostResultDto createPayment(long companyId, long userId, OutgoingPaymentRequest req) {
        if (req == null || req.idempotencyKey == null || req.idempotencyKey.length() == 0) {
            return bad("IDEMPOTENCY_REQUIRED", "Idempotency-Key required");
        }
        PaymentCreateWork w = new PaymentCreateWork();
        w.companyId = companyId;
        w.userId = userId;
        w.req = req;
        return ErpPosting.runPostingWork(w);
    }

    static PostResultDto postPayment(long companyId, long userId, long paymentId, String idempotencyKey) {
        PaymentPostWork w = new PaymentPostWork();
        w.companyId = companyId;
        w.userId = userId;
        w.paymentId = paymentId;
        w.idempotencyKey = idempotencyKey;
        return ErpPosting.runPostingWork(w);
    }

    static PostResultDto allocatePayment(long companyId, long userId, SettlementAllocateRequest req) {
        if (req == null || req.idempotencyKey == null || req.idempotencyKey.length() == 0) {
            return bad("IDEMPOTENCY_REQUIRED", "Idempotency-Key required");
        }
        PaymentAllocWork w = new PaymentAllocWork();
        w.companyId = companyId;
        w.userId = userId;
        w.req = req;
        return ErpPosting.runPostingWork(w);
    }

    static PostResultDto reversePaymentAlloc(long companyId, long userId, SettlementReverseRequest req) {
        if (req == null || req.idempotencyKey == null || req.idempotencyKey.length() == 0) {
            return bad("IDEMPOTENCY_REQUIRED", "Idempotency-Key required");
        }
        PaymentReverseWork w = new PaymentReverseWork();
        w.companyId = companyId;
        w.userId = userId;
        w.req = req;
        return ErpPosting.runPostingWork(w);
    }

    /**
     * Void entire posted receipt: reverse ACTIVE allocs, reverse bank movement, reverse GL JE,
     * then mark CANCELLED. Distinct from reverseReceiptAlloc (unallocate only; cash stays).
     */
    static PostResultDto voidReceipt(long companyId, long userId, PaymentVoidRequest req) {
        if (req == null || req.idempotencyKey == null || req.idempotencyKey.length() == 0) {
            return bad("IDEMPOTENCY_REQUIRED", "Idempotency-Key required");
        }
        if (req.paymentId <= 0L) {
            return bad("INVALID", "paymentId required");
        }
        ReceiptVoidWork w = new ReceiptVoidWork();
        w.companyId = companyId;
        w.userId = userId;
        w.req = req;
        return ErpPosting.runPostingWork(w);
    }

    /** Void entire posted outgoing payment (mirror of voidReceipt). */
    static PostResultDto voidPayment(long companyId, long userId, PaymentVoidRequest req) {
        if (req == null || req.idempotencyKey == null || req.idempotencyKey.length() == 0) {
            return bad("IDEMPOTENCY_REQUIRED", "Idempotency-Key required");
        }
        if (req.paymentId <= 0L) {
            return bad("INVALID", "paymentId required");
        }
        PaymentVoidWork w = new PaymentVoidWork();
        w.companyId = companyId;
        w.userId = userId;
        w.req = req;
        return ErpPosting.runPostingWork(w);
    }

    /**
     * Reverse one ACTIVE AR alloc row inside current txn; skips already-reversed.
     * Restores invoice open/paid/status/settlement; updates payment alloc split.
     */
    static void reverseActiveArAllocConn(DbConnection conn, long companyId, long userId, long allocId,
            long now, String reason, String idempotencyKey) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(allocId));
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult ar = ErpDb.queryConn(conn,
                "SELECT id, payment_id, invoice_id, allocated_amount, status, reversed_by_id FROM ar_settlement_allocs WHERE id=? AND company_id=?",
                p);
        if (ar.rows.size() == 0) {
            throw new TransactionException("allocation not found");
        }
        long paymentId = ar.rows.get(0).getLong(1);
        long invoiceId = ar.rows.get(0).getLong(2);
        long amount = ar.rows.get(0).getLong(3);
        String st = ar.rows.get(0).getString(4);
        long revBy = ar.rows.get(0).getLong(5);
        if (st.equals(AllocationRowStatus.REVERSED) || revBy > 0L) {
            return;
        }
        ArrayList<Object> pp = new ArrayList<Object>();
        pp.add(CryptoParse.longToString(paymentId));
        pp.add(CryptoParse.longToString(companyId));
        DbQueryResult pr = ErpDb.queryConn(conn,
                "SELECT allocated_amount, unallocated_amount, status FROM incoming_payments WHERE id=? AND company_id=?",
                pp);
        if (pr.rows.size() == 0) {
            throw new TransactionException("receipt not found");
        }
        long allocated = pr.rows.get(0).getLong(0);
        long unalloc = pr.rows.get(0).getLong(1);
        String lockSql = "SELECT open_amount, paid_amount, total_amount, adjusted_amount, status FROM ar_invoices WHERE id="
                + CryptoParse.longToString(invoiceId) + " AND company_id=" + CryptoParse.longToString(companyId);
        if (Erp.dialect.equals("postgresql")) {
            lockSql = lockSql + " FOR UPDATE";
        }
        DbQueryResult ir = ErpDb.queryConn(conn, lockSql, new ArrayList<Object>());
        if (ir.rows.size() == 0) {
            throw new TransactionException("AR not found");
        }
        long open = ir.rows.get(0).getLong(0);
        long paid = ir.rows.get(0).getLong(1);
        long total = ir.rows.get(0).getLong(2);
        long adj = ir.rows.get(0).getLong(3);
        String invSt = ir.rows.get(0).getString(4);
        if (amount > paid) {
            throw new TransactionException("reverse exceeds paid");
        }
        long newPaid = paid - amount;
        long newOpen = open + amount;
        long effective = total + adj;
        String settle = settlementOf(newOpen, newPaid, effective);
        String newInvSt = invSt;
        if (invSt.equals(InvoiceStatus.PROVISIONAL) == false) {
            if (newPaid <= 0L) {
                newInvSt = InvoiceStatus.POSTED;
            } else {
                if (newOpen <= 0L) {
                    newInvSt = InvoiceStatus.PAID;
                } else {
                    newInvSt = InvoiceStatus.PARTIALLY_PAID;
                }
            }
        }
        ErpDb.execConn(conn,
                "UPDATE ar_invoices SET paid_amount=" + CryptoParse.longToString(newPaid)
                        + ", open_amount=" + CryptoParse.longToString(newOpen)
                        + ", settlement_status='" + settle + "'"
                        + ", status='" + newInvSt + "'"
                        + " WHERE id=" + CryptoParse.longToString(invoiceId)
                        + " AND company_id=" + CryptoParse.longToString(companyId)
                        + " AND paid_amount=" + CryptoParse.longToString(paid)
                        + " AND open_amount=" + CryptoParse.longToString(open),
                new ArrayList<Object>());
        String rsn = reason == null ? "" : reason;
        ArrayList<Object> revP = new ArrayList<Object>();
        revP.add(CryptoParse.longToString(companyId));
        revP.add(CryptoParse.longToString(paymentId));
        revP.add(CryptoParse.longToString(invoiceId));
        revP.add(CryptoParse.longToString(amount));
        revP.add(AllocationRowStatus.REVERSED);
        revP.add(CryptoParse.longToString(allocId));
        revP.add(rsn);
        revP.add(CryptoParse.longToString(userId));
        revP.add(CryptoParse.longToString(now));
        revP.add(idempotencyKey + "-a" + CryptoParse.longToString(allocId));
        long revId = ErpDb.insertReturningIdConn(conn,
                "INSERT INTO ar_settlement_allocs(company_id, payment_id, invoice_id, allocated_amount, status, reverse_of_id, reason, created_by, created_at, idempotency_key) VALUES (?,?,?,?,?,?,?,?,?,?)",
                revP);
        ErpDb.execConn(conn,
                "UPDATE ar_settlement_allocs SET status='" + AllocationRowStatus.REVERSED
                        + "', reversed_by_id=" + CryptoParse.longToString(revId)
                        + " WHERE id=" + CryptoParse.longToString(allocId),
                new ArrayList<Object>());
        long newAlloc = allocated - amount;
        long newUn = unalloc + amount;
        ErpDb.execConn(conn,
                "UPDATE incoming_payments SET allocated_amount=" + CryptoParse.longToString(newAlloc)
                        + ", unallocated_amount=" + CryptoParse.longToString(newUn)
                        + " WHERE id=" + CryptoParse.longToString(paymentId)
                        + " AND company_id=" + CryptoParse.longToString(companyId)
                        + " AND allocated_amount=" + CryptoParse.longToString(allocated),
                new ArrayList<Object>());
    }

    static void reverseActiveApAllocConn(DbConnection conn, long companyId, long userId, long allocId,
            long now, String reason, String idempotencyKey) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(allocId));
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult ar = ErpDb.queryConn(conn,
                "SELECT id, payment_id, invoice_id, allocated_amount, status, reversed_by_id FROM ap_settlement_allocs WHERE id=? AND company_id=?",
                p);
        if (ar.rows.size() == 0) {
            throw new TransactionException("allocation not found");
        }
        long paymentId = ar.rows.get(0).getLong(1);
        long invoiceId = ar.rows.get(0).getLong(2);
        long amount = ar.rows.get(0).getLong(3);
        String st = ar.rows.get(0).getString(4);
        long revBy = ar.rows.get(0).getLong(5);
        if (st.equals(AllocationRowStatus.REVERSED) || revBy > 0L) {
            return;
        }
        ArrayList<Object> pp = new ArrayList<Object>();
        pp.add(CryptoParse.longToString(paymentId));
        pp.add(CryptoParse.longToString(companyId));
        DbQueryResult pr = ErpDb.queryConn(conn,
                "SELECT allocated_amount, unallocated_amount FROM outgoing_payments WHERE id=? AND company_id=?",
                pp);
        long allocated = pr.rows.get(0).getLong(0);
        long unalloc = pr.rows.get(0).getLong(1);
        String lockSql = "SELECT open_amount, paid_amount, total_amount, adjusted_amount, status FROM ap_invoices WHERE id="
                + CryptoParse.longToString(invoiceId) + " AND company_id=" + CryptoParse.longToString(companyId);
        if (Erp.dialect.equals("postgresql")) {
            lockSql = lockSql + " FOR UPDATE";
        }
        DbQueryResult ir = ErpDb.queryConn(conn, lockSql, new ArrayList<Object>());
        long open = ir.rows.get(0).getLong(0);
        long paid = ir.rows.get(0).getLong(1);
        long total = ir.rows.get(0).getLong(2);
        long adj = ir.rows.get(0).getLong(3);
        String invSt = ir.rows.get(0).getString(4);
        long newPaid = paid - amount;
        long newOpen = open + amount;
        String settle = settlementOf(newOpen, newPaid, total + adj);
        String newInvSt = invSt;
        if (invSt.equals(InvoiceStatus.PROVISIONAL) == false) {
            if (newPaid <= 0L) {
                newInvSt = InvoiceStatus.POSTED;
            } else {
                if (newOpen <= 0L) {
                    newInvSt = InvoiceStatus.PAID;
                } else {
                    newInvSt = InvoiceStatus.PARTIALLY_PAID;
                }
            }
        }
        ErpDb.execConn(conn,
                "UPDATE ap_invoices SET paid_amount=" + CryptoParse.longToString(newPaid)
                        + ", open_amount=" + CryptoParse.longToString(newOpen)
                        + ", settlement_status='" + settle + "'"
                        + ", status='" + newInvSt + "'"
                        + " WHERE id=" + CryptoParse.longToString(invoiceId)
                        + " AND paid_amount=" + CryptoParse.longToString(paid)
                        + " AND open_amount=" + CryptoParse.longToString(open),
                new ArrayList<Object>());
        String rsn = reason == null ? "" : reason;
        ArrayList<Object> revP = new ArrayList<Object>();
        revP.add(CryptoParse.longToString(companyId));
        revP.add(CryptoParse.longToString(paymentId));
        revP.add(CryptoParse.longToString(invoiceId));
        revP.add(CryptoParse.longToString(amount));
        revP.add(AllocationRowStatus.REVERSED);
        revP.add(CryptoParse.longToString(allocId));
        revP.add(rsn);
        revP.add(CryptoParse.longToString(userId));
        revP.add(CryptoParse.longToString(now));
        revP.add(idempotencyKey + "-a" + CryptoParse.longToString(allocId));
        long revId = ErpDb.insertReturningIdConn(conn,
                "INSERT INTO ap_settlement_allocs(company_id, payment_id, invoice_id, allocated_amount, status, reverse_of_id, reason, created_by, created_at, idempotency_key) VALUES (?,?,?,?,?,?,?,?,?,?)",
                revP);
        ErpDb.execConn(conn,
                "UPDATE ap_settlement_allocs SET status='" + AllocationRowStatus.REVERSED
                        + "', reversed_by_id=" + CryptoParse.longToString(revId)
                        + " WHERE id=" + CryptoParse.longToString(allocId),
                new ArrayList<Object>());
        ErpDb.execConn(conn,
                "UPDATE outgoing_payments SET allocated_amount=" + CryptoParse.longToString(allocated - amount)
                        + ", unallocated_amount=" + CryptoParse.longToString(unalloc + amount)
                        + " WHERE id=" + CryptoParse.longToString(paymentId)
                        + " AND allocated_amount=" + CryptoParse.longToString(allocated),
                new ArrayList<Object>());
    }

    static long findPaymentJournalIdConn(DbConnection conn, long companyId, String sourceType, long payId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(sourceType);
        p.add(CryptoParse.longToString(payId));
        Object v = ErpDb.queryOneConn(conn,
                "SELECT id FROM journal_entries WHERE company_id=? AND source_type=? AND source_id=? AND status='POSTED' ORDER BY id ASC LIMIT 1",
                p, new LongMapper());
        return ErpNum.asLong(v);
    }

    static long findOriginalBankMovementIdConn(DbConnection conn, long companyId, String sourceType, long payId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(sourceType);
        p.add(CryptoParse.longToString(payId));
        Object v = ErpDb.queryOneConn(conn,
                "SELECT id FROM bank_cash_movements WHERE company_id=? AND source_type=? AND source_id=? AND COALESCE(reverse_of_id,0)=0 ORDER BY id ASC LIMIT 1",
                p, new LongMapper());
        return ErpNum.asLong(v);
    }

    /**
     * Adjust AR open via linked credit (negative adjust_amount). Does not invent cash refund.
     * Idempotent on (source_type, source_id, AR_ADJUST).
     */
    static void adjustArConn(DbConnection conn, long companyId, long sourceDeliveryId, long creditCents,
            long salesReturnId, long userId, long now) {
        if (creditCents <= 0L || sourceDeliveryId <= 0L) {
            return;
        }
        ArrayList<Object> ex = new ArrayList<Object>();
        ex.add(CryptoParse.longToString(companyId));
        ex.add(CryptoParse.longToString(sourceDeliveryId));
        DbQueryResult r = ErpDb.queryConn(conn,
                "SELECT id, open_amount, paid_amount, total_amount, adjusted_amount, status FROM ar_invoices WHERE company_id=? AND source_delivery_id=?",
                ex);
        if (r.rows.size() == 0) {
            return;
        }
        Row row = r.rows.get(0);
        long arId = row.getLong(0);
        long open = row.getLong(1);
        long paid = row.getLong(2);
        long total = row.getLong(3);
        long adj = row.getLong(4);
        String st = row.getString(5);
        String ik = "ar-adj-sr-" + CryptoParse.longToString(salesReturnId);
        ArrayList<Object> ikp = new ArrayList<Object>();
        ikp.add(CryptoParse.longToString(companyId));
        ikp.add(ik);
        Object exists = ErpDb.queryOneConn(conn,
                "SELECT id FROM ar_ap_adjustments WHERE company_id=? AND idempotency_key=?", ikp, new LongMapper());
        if (exists != null) {
            return;
        }
        long apply = creditCents;
        if (apply > open) {
            /* Over-settled credit becomes further negative open prevention: cap to open; remainder is credit memo note. */
            apply = open;
        }
        long newAdj = adj - apply;
        long newOpen = open - apply;
        if (newOpen < 0L) {
            throw new TransactionException("AR adjust would create unexplained negative open");
        }
        long effective = total + newAdj;
        String settle = settlementOf(newOpen, paid, effective);
        ErpDb.execConn(conn,
                "UPDATE ar_invoices SET adjusted_amount=" + CryptoParse.longToString(newAdj)
                        + ", open_amount=" + CryptoParse.longToString(newOpen)
                        + ", settlement_status='" + settle + "'"
                        + " WHERE id=" + CryptoParse.longToString(arId)
                        + " AND company_id=" + CryptoParse.longToString(companyId)
                        + " AND open_amount=" + CryptoParse.longToString(open),
                new ArrayList<Object>());
        ArrayList<Object> ap = new ArrayList<Object>();
        ap.add(CryptoParse.longToString(companyId));
        ap.add("AR");
        ap.add(CryptoParse.longToString(arId));
        ap.add(CryptoParse.longToString(0L - apply));
        ap.add("sales return credit");
        ap.add("SALES_RETURN");
        ap.add(CryptoParse.longToString(salesReturnId));
        ap.add(CryptoParse.longToString(userId));
        ap.add(CryptoParse.longToString(now));
        ap.add(CryptoParse.longToString(now));
        ap.add(ik);
        ErpDb.execConn(conn,
                "INSERT INTO ar_ap_adjustments(company_id, side, invoice_id, adjust_amount, reason, source_type, source_id, created_by, biz_time, created_at, idempotency_key) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                ap);
        /* If original was already settled, leftover creditCents-apply is customer credit (unapplied) — recorded in notes only this phase. */
        if (creditCents > apply) {
            long leftover = creditCents - apply;
            ErpDb.execConn(conn,
                    "UPDATE ar_invoices SET notes=notes || ' CREDIT_BALANCE="
                            + CryptoParse.longToString(leftover) + "'"
                            + " WHERE id=" + CryptoParse.longToString(arId),
                    new ArrayList<Object>());
        }
        recordSourceEventConn(conn, companyId, "SALES_RETURN", salesReturnId, FinanceEventType.AR_ADJUST, arId, now);
    }

    static void recordSourceEventConn(DbConnection conn, long companyId, String sourceType, long sourceId,
            String eventType, long refId, long now) {
        try {
            ArrayList<Object> p = new ArrayList<Object>();
            p.add(CryptoParse.longToString(companyId));
            p.add(sourceType);
            p.add(CryptoParse.longToString(sourceId));
            p.add(eventType);
            p.add(CryptoParse.longToString(refId));
            p.add(CryptoParse.longToString(now));
            ErpDb.execConn(conn,
                    "INSERT INTO finance_source_events(company_id, source_type, source_id, event_type, ref_id, created_at) VALUES (?,?,?,?,?,?)",
                    p);
        } catch (Exception ex) {
            String m = "";
            if (ex.getMessage() != null) {
                m = ex.getMessage();
            }
            if (m.indexOf("UNIQUE") >= 0 || m.indexOf("unique") >= 0 || m.indexOf("constraint") >= 0) {
                throw new TransactionException("duplicate finance source event " + sourceType + "/" + eventType);
            }
            throw new TransactionException("finance source event failed: " + m);
        }
    }

    static void applyBankMovementConn(DbConnection conn, long companyId, long bankAccountId, long signedAmount,
            String movementType, long bizDate, String sourceType, long sourceId, long partyId, long userId,
            long now, String idempotencyKey, String notes) {
        applyBankMovementConn(conn, companyId, bankAccountId, signedAmount, movementType, bizDate, sourceType,
                sourceId, partyId, userId, now, idempotencyKey, notes, 0L);
    }

    static void applyBankMovementConn(DbConnection conn, long companyId, long bankAccountId, long signedAmount,
            String movementType, long bizDate, String sourceType, long sourceId, long partyId, long userId,
            long now, String idempotencyKey, String notes, long reverseOfId) {
        ArrayList<Object> bp = new ArrayList<Object>();
        bp.add(CryptoParse.longToString(bankAccountId));
        bp.add(CryptoParse.longToString(companyId));
        String lockSql = "SELECT balance, allow_overdraft, active FROM bank_accounts WHERE id=? AND company_id=?";
        if (Erp.dialect.equals("postgresql")) {
            lockSql = lockSql + " FOR UPDATE";
        }
        DbQueryResult br = ErpDb.queryConn(conn, lockSql, bp);
        if (br.rows.size() == 0) {
            throw new TransactionException("bank account invalid");
        }
        long bal = br.rows.get(0).getLong(0);
        int allowOd = br.rows.get(0).getInt(1);
        int active = br.rows.get(0).getInt(2);
        if (active == 0) {
            throw new TransactionException("bank account inactive");
        }
        long next = bal + signedAmount;
        if (next < 0L && allowOd == 0) {
            throw new TransactionException("bank overdraft not allowed");
        }
        ErpDb.execConn(conn,
                "UPDATE bank_accounts SET balance=" + CryptoParse.longToString(next)
                        + " WHERE id=" + CryptoParse.longToString(bankAccountId)
                        + " AND company_id=" + CryptoParse.longToString(companyId)
                        + " AND balance=" + CryptoParse.longToString(bal),
                new ArrayList<Object>());
        ArrayList<Object> mp = new ArrayList<Object>();
        mp.add(CryptoParse.longToString(companyId));
        mp.add(CryptoParse.longToString(bankAccountId));
        mp.add(movementType);
        mp.add(CryptoParse.longToString(signedAmount));
        mp.add(CryptoParse.longToString(next));
        mp.add(CryptoParse.longToString(bizDate));
        mp.add(sourceType);
        mp.add(CryptoParse.longToString(sourceId));
        mp.add(CryptoParse.longToString(partyId));
        mp.add(notes == null ? "" : notes);
        mp.add(CryptoParse.longToString(userId));
        mp.add(CryptoParse.longToString(now));
        mp.add(idempotencyKey);
        mp.add(CryptoParse.longToString(reverseOfId));
        ErpDb.execConn(conn,
                "INSERT INTO bank_cash_movements(company_id, bank_account_id, movement_type, amount, balance_after, biz_date, source_type, source_id, party_id, notes, created_by, created_at, idempotency_key, reverse_of_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                mp);
    }

    static void applyArAllocConn(DbConnection conn, long companyId, long paymentId, long customerId,
            long invoiceId, long amount, long userId, long now, String idempotencyKey, String reason) {
        if (amount <= 0L) {
            throw new TransactionException("allocatedAmount must be > 0");
        }
        String lockSql = "SELECT id, open_amount, paid_amount, total_amount, adjusted_amount, status, customer_id, settlement_status FROM ar_invoices WHERE id="
                + CryptoParse.longToString(invoiceId) + " AND company_id="
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
        long total = irow.getLong(3);
        long adj = irow.getLong(4);
        String st = irow.getString(5);
        long cust = irow.getLong(6);
        if (cust != customerId) {
            throw new TransactionException("AR customer mismatch");
        }
        if (invoiceOpenForSettle(st) == 0) {
            throw new TransactionException("invoice not open for settlement");
        }
        if (amount > open) {
            throw new TransactionException("over-allocation");
        }
        long newPaid = paid + amount;
        long newOpen = open - amount;
        long effective = total + adj;
        String settle = settlementOf(newOpen, newPaid, effective);
        String newSt = st;
        if (newOpen == 0L) {
            newSt = InvoiceStatus.PAID;
        } else {
            if (newPaid > 0L) {
                newSt = InvoiceStatus.PARTIALLY_PAID;
            }
        }
        ErpDb.execConn(conn,
                "UPDATE ar_invoices SET paid_amount=" + CryptoParse.longToString(newPaid)
                        + ", open_amount=" + CryptoParse.longToString(newOpen)
                        + ", status='" + newSt + "'"
                        + ", settlement_status='" + settle + "'"
                        + " WHERE id=" + CryptoParse.longToString(invoiceId)
                        + " AND company_id=" + CryptoParse.longToString(companyId)
                        + " AND open_amount=" + CryptoParse.longToString(open),
                new ArrayList<Object>());
        ArrayList<Object> ap = new ArrayList<Object>();
        ap.add(CryptoParse.longToString(companyId));
        ap.add(CryptoParse.longToString(paymentId));
        ap.add(CryptoParse.longToString(invoiceId));
        ap.add(CryptoParse.longToString(amount));
        ap.add(AllocationRowStatus.ACTIVE);
        ap.add(reason == null ? "" : reason);
        ap.add(CryptoParse.longToString(userId));
        ap.add(CryptoParse.longToString(now));
        ap.add(idempotencyKey);
        ErpDb.execConn(conn,
                "INSERT INTO ar_settlement_allocs(company_id, payment_id, invoice_id, allocated_amount, status, reason, created_by, created_at, idempotency_key) VALUES (?,?,?,?,?,?,?,?,?)",
                ap);
        /* Legacy mirror table used by older reports / finance tests. */
        ErpDb.execConn(conn,
                "INSERT INTO incoming_payment_allocations(company_id, payment_id, invoice_id, allocated_amount) VALUES ("
                        + CryptoParse.longToString(companyId) + ","
                        + CryptoParse.longToString(paymentId) + ","
                        + CryptoParse.longToString(invoiceId) + ","
                        + CryptoParse.longToString(amount) + ")",
                new ArrayList<Object>());
    }

    static void applyApAllocConn(DbConnection conn, long companyId, long paymentId, long supplierId,
            long invoiceId, long amount, long userId, long now, String idempotencyKey, String reason) {
        if (amount <= 0L) {
            throw new TransactionException("allocatedAmount must be > 0");
        }
        String lockSql = "SELECT id, open_amount, paid_amount, total_amount, adjusted_amount, status, supplier_id FROM ap_invoices WHERE id="
                + CryptoParse.longToString(invoiceId) + " AND company_id="
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
        long total = irow.getLong(3);
        long adj = irow.getLong(4);
        String st = irow.getString(5);
        long sup = irow.getLong(6);
        if (sup != supplierId) {
            throw new TransactionException("AP supplier mismatch");
        }
        if (invoiceOpenForSettle(st) == 0) {
            throw new TransactionException("invoice not open for settlement");
        }
        if (amount > open) {
            throw new TransactionException("over-allocation");
        }
        long newPaid = paid + amount;
        long newOpen = open - amount;
        long effective = total + adj;
        String settle = settlementOf(newOpen, newPaid, effective);
        String newSt = st;
        if (newOpen == 0L) {
            newSt = InvoiceStatus.PAID;
        } else {
            if (newPaid > 0L) {
                newSt = InvoiceStatus.PARTIALLY_PAID;
            }
        }
        ErpDb.execConn(conn,
                "UPDATE ap_invoices SET paid_amount=" + CryptoParse.longToString(newPaid)
                        + ", open_amount=" + CryptoParse.longToString(newOpen)
                        + ", status='" + newSt + "'"
                        + ", settlement_status='" + settle + "'"
                        + " WHERE id=" + CryptoParse.longToString(invoiceId)
                        + " AND company_id=" + CryptoParse.longToString(companyId)
                        + " AND open_amount=" + CryptoParse.longToString(open),
                new ArrayList<Object>());
        ArrayList<Object> ap = new ArrayList<Object>();
        ap.add(CryptoParse.longToString(companyId));
        ap.add(CryptoParse.longToString(paymentId));
        ap.add(CryptoParse.longToString(invoiceId));
        ap.add(CryptoParse.longToString(amount));
        ap.add(AllocationRowStatus.ACTIVE);
        ap.add(reason == null ? "" : reason);
        ap.add(CryptoParse.longToString(userId));
        ap.add(CryptoParse.longToString(now));
        ap.add(idempotencyKey);
        ErpDb.execConn(conn,
                "INSERT INTO ap_settlement_allocs(company_id, payment_id, invoice_id, allocated_amount, status, reason, created_by, created_at, idempotency_key) VALUES (?,?,?,?,?,?,?,?,?)",
                ap);
        ErpDb.execConn(conn,
                "INSERT INTO outgoing_payment_allocations(company_id, payment_id, invoice_id, allocated_amount) VALUES ("
                        + CryptoParse.longToString(companyId) + ","
                        + CryptoParse.longToString(paymentId) + ","
                        + CryptoParse.longToString(invoiceId) + ","
                        + CryptoParse.longToString(amount) + ")",
                new ArrayList<Object>());
    }

    /** Bank Dr / AR Cr — idempotent on je-ip-{payId}; must run in same txn as bank movement. */
    static void postIncomingPaymentJournalConn(DbConnection conn, long companyId, long userId, long payId,
            long bankGl, long amount, long customerId, long paymentDate) {
        long ar = ErpFinanceCore.mappedAccountIdConn(conn, companyId, AccountMapKey.AR);
        PostingRequest jeReq = new PostingRequest();
        jeReq.sourceType = JournalSource.INCOMING_PAYMENT;
        jeReq.sourceId = payId;
        jeReq.postingDate = paymentDate;
        jeReq.documentDate = paymentDate;
        jeReq.description = "Incoming payment";
        jeReq.idempotencyKey = "je-ip-" + CryptoParse.longToString(payId);
        jeReq.lines = new ArrayList<Object>();
        jeReq.lines.add(ErpFinanceCore.line(bankGl, amount, 0L, customerId, "Bank"));
        jeReq.lines.add(ErpFinanceCore.line(ar, 0L, amount, customerId, "AR credit"));
        PostResultDto je = ErpFinanceCore.postJournalConn(conn, companyId, userId, jeReq);
        if (je == null || je.ok == 0) {
            String msg = "incoming payment GL failed";
            if (je != null && je.message != null && je.message.length() > 0) {
                msg = je.message;
            }
            throw new TransactionException(msg);
        }
    }

    /** AP Dr / Bank Cr — idempotent on je-op-{payId}. */
    static void postOutgoingPaymentJournalConn(DbConnection conn, long companyId, long userId, long payId,
            long bankGl, long amount, long supplierId, long paymentDate) {
        long ap = ErpFinanceCore.mappedAccountIdConn(conn, companyId, AccountMapKey.AP);
        PostingRequest jeReq = new PostingRequest();
        jeReq.sourceType = JournalSource.OUTGOING_PAYMENT;
        jeReq.sourceId = payId;
        jeReq.postingDate = paymentDate;
        jeReq.documentDate = paymentDate;
        jeReq.description = "Outgoing payment";
        jeReq.idempotencyKey = "je-op-" + CryptoParse.longToString(payId);
        jeReq.lines = new ArrayList<Object>();
        jeReq.lines.add(ErpFinanceCore.line(ap, amount, 0L, supplierId, "AP debit"));
        jeReq.lines.add(ErpFinanceCore.line(bankGl, 0L, amount, supplierId, "Bank"));
        PostResultDto je = ErpFinanceCore.postJournalConn(conn, companyId, userId, jeReq);
        if (je == null || je.ok == 0) {
            String msg = "outgoing payment GL failed";
            if (je != null && je.message != null && je.message.length() > 0) {
                msg = je.message;
            }
            throw new TransactionException(msg);
        }
    }

    static void sortAllocByInvoiceId(ArrayList<Object> allocations) {
        if (allocations == null) {
            return;
        }
        int n = allocations.size();
        int i = 0;
        while (i < n) {
            int j = i + 1;
            while (j < n) {
                PaymentAllocationInput a = (PaymentAllocationInput) allocations.get(i);
                PaymentAllocationInput b = (PaymentAllocationInput) allocations.get(j);
                if (b.invoiceId < a.invoiceId) {
                    allocations.set(i, b);
                    allocations.set(j, a);
                }
                j = j + 1;
            }
            i = i + 1;
        }
    }

    static PostResultDto bad(String code, String message) {
        PostResultDto r = new PostResultDto();
        r.ok = 0;
        r.code = code;
        r.message = message;
        return r;
    }

    static PostResultDto okDoc(String code, String message, String documentJson) {
        PostResultDto r = new PostResultDto();
        r.ok = 1;
        r.code = code;
        r.message = message;
        r.documentJson = documentJson;
        return r;
    }

    static long bankBalance(long companyId, long bankAccountId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(bankAccountId));
        p.add(CryptoParse.longToString(companyId));
        Object v = ErpDb.queryOne("SELECT balance FROM bank_accounts WHERE id=? AND company_id=?", p, new LongMapper());
        return ErpNum.asLong(v);
    }

    static long arOpen(long companyId, long invoiceId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(invoiceId));
        p.add(CryptoParse.longToString(companyId));
        Object v = ErpDb.queryOne("SELECT open_amount FROM ar_invoices WHERE id=? AND company_id=?", p, new LongMapper());
        return ErpNum.asLong(v);
    }

    static long apOpen(long companyId, long invoiceId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(invoiceId));
        p.add(CryptoParse.longToString(companyId));
        Object v = ErpDb.queryOne("SELECT open_amount FROM ap_invoices WHERE id=? AND company_id=?", p, new LongMapper());
        return ErpNum.asLong(v);
    }

    static long receiptUnallocated(long companyId, long paymentId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(paymentId));
        p.add(CryptoParse.longToString(companyId));
        Object v = ErpDb.queryOne(
                "SELECT unallocated_amount FROM incoming_payments WHERE id=? AND company_id=?", p, new LongMapper());
        return ErpNum.asLong(v);
    }

    static long paymentUnallocated(long companyId, long paymentId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(paymentId));
        p.add(CryptoParse.longToString(companyId));
        Object v = ErpDb.queryOne(
                "SELECT unallocated_amount FROM outgoing_payments WHERE id=? AND company_id=?", p, new LongMapper());
        return ErpNum.asLong(v);
    }

    static long findActiveAllocId(long companyId, long paymentId, long invoiceId, String side) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(CryptoParse.longToString(paymentId));
        p.add(CryptoParse.longToString(invoiceId));
        String tbl = "ar_settlement_allocs";
        if (side.equals("AP")) {
            tbl = "ap_settlement_allocs";
        }
        Object v = ErpDb.queryOne(
                "SELECT id FROM " + tbl + " WHERE company_id=? AND payment_id=? AND invoice_id=? AND status='ACTIVE' AND reverse_of_id=0 ORDER BY id DESC LIMIT 1",
                p, new LongMapper());
        return ErpNum.asLong(v);
    }

    /** Integrity: AR open = total + adjusted - paid; alloc sum matches paid; bank = movements. */
    static int runIntegrityChecks(long companyId) {
        int pass = 1;
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult ar = ErpDb.query(
                "SELECT id, total_amount, adjusted_amount, paid_amount, open_amount FROM ar_invoices WHERE company_id=?",
                p);
        int i = 0;
        while (i < ar.rows.size()) {
            Row row = ar.rows.get(i);
            long id = row.getLong(0);
            long total = row.getLong(1);
            long adj = row.getLong(2);
            long paid = row.getLong(3);
            long open = row.getLong(4);
            if (total + adj - paid != open) {
                System.out.println("FAIL integrity:ar_balance id=" + CryptoParse.longToString(id)
                        + " total=" + CryptoParse.longToString(total)
                        + " adj=" + CryptoParse.longToString(adj)
                        + " paid=" + CryptoParse.longToString(paid)
                        + " open=" + CryptoParse.longToString(open));
                pass = 0;
            }
            ArrayList<Object> ap = new ArrayList<Object>();
            ap.add(CryptoParse.longToString(companyId));
            ap.add(CryptoParse.longToString(id));
            Object allocSum = ErpDb.queryOne(
                    "SELECT COALESCE(SUM(CASE WHEN status='ACTIVE' THEN allocated_amount ELSE 0 END),0) FROM ar_settlement_allocs WHERE company_id=? AND invoice_id=?",
                    ap, new LongMapper());
            if (ErpNum.asLong(allocSum) != paid) {
                System.out.println("FAIL integrity:ar_alloc id=" + CryptoParse.longToString(id)
                        + " paid=" + CryptoParse.longToString(paid)
                        + " alloc=" + CryptoParse.longToString(ErpNum.asLong(allocSum)));
                pass = 0;
            }
            i = i + 1;
        }
        DbQueryResult ip = ErpDb.query(
                "SELECT id, amount, allocated_amount, unallocated_amount, status FROM incoming_payments WHERE company_id=?",
                p);
        i = 0;
        while (i < ip.rows.size()) {
            Row row = ip.rows.get(i);
            long id = row.getLong(0);
            long amount = row.getLong(1);
            long allocated = row.getLong(2);
            long unalloc = row.getLong(3);
            String st = row.getString(4);
            if (st.equals(PaymentStatus.POSTED) && amount != allocated + unalloc) {
                System.out.println("FAIL integrity:ip_split id=" + CryptoParse.longToString(id));
                pass = 0;
            }
            i = i + 1;
        }
        DbQueryResult banks = ErpDb.query(
                "SELECT id, balance FROM bank_accounts WHERE company_id=?", p);
        i = 0;
        while (i < banks.rows.size()) {
            Row row = banks.rows.get(i);
            long bid = row.getLong(0);
            long bal = row.getLong(1);
            ArrayList<Object> mp = new ArrayList<Object>();
            mp.add(CryptoParse.longToString(companyId));
            mp.add(CryptoParse.longToString(bid));
            Object mov = ErpDb.queryOne(
                    "SELECT COALESCE(SUM(amount),0) FROM bank_cash_movements WHERE company_id=? AND bank_account_id=?",
                    mp, new LongMapper());
            /* Opening balance may pre-exist without movements; only check when movements exist. */
            ArrayList<Object> cntP = new ArrayList<Object>();
            cntP.add(CryptoParse.longToString(companyId));
            cntP.add(CryptoParse.longToString(bid));
            Object cnt = ErpDb.queryOne(
                    "SELECT COUNT(*) FROM bank_cash_movements WHERE company_id=? AND bank_account_id=?", cntP,
                    new LongMapper());
            if (ErpNum.asLong(cnt) > 0L) {
                /* Reconstruct: first movement's balance_after - amount = prior; sum(amount) + opening. Use last balance_after. */
                Object last = ErpDb.queryOne(
                        "SELECT balance_after FROM bank_cash_movements WHERE company_id=? AND bank_account_id=? ORDER BY id DESC LIMIT 1",
                        mp, new LongMapper());
                if (ErpNum.asLong(last) != bal) {
                    System.out.println("FAIL integrity:bank id=" + CryptoParse.longToString(bid)
                            + " bal=" + CryptoParse.longToString(bal)
                            + " lastMov=" + CryptoParse.longToString(ErpNum.asLong(last)));
                    pass = 0;
                }
            }
            i = i + 1;
        }
        if (pass != 0) {
            System.out.println("PASS integrity:settlement");
        }
        return pass;
    }

    static String partyStatementJson(long companyId, long partnerId, String side, long fromTs, long toTs) {
        /* Period statement: opening open sum before fromTs + period movements. Simplified: list invoices + payments. */
        String out = "{\"partnerId\":" + ErpJson.num(partnerId) + ",\"side\":" + ErpJson.str(side);
        long openSum = 0L;
        if (side.equals("AR")) {
            ArrayList<Object> p = new ArrayList<Object>();
            p.add(CryptoParse.longToString(companyId));
            p.add(CryptoParse.longToString(partnerId));
            Object o = ErpDb.queryOne(
                    "SELECT COALESCE(SUM(open_amount),0) FROM ar_invoices WHERE company_id=? AND customer_id=?", p,
                    new LongMapper());
            openSum = ErpNum.asLong(o);
        } else {
            ArrayList<Object> p = new ArrayList<Object>();
            p.add(CryptoParse.longToString(companyId));
            p.add(CryptoParse.longToString(partnerId));
            Object o = ErpDb.queryOne(
                    "SELECT COALESCE(SUM(open_amount),0) FROM ap_invoices WHERE company_id=? AND supplier_id=?", p,
                    new LongMapper());
            openSum = ErpNum.asLong(o);
        }
        out = out + ",\"openBalance\":" + ErpJson.num(openSum);
        out = out + ",\"note\":" + ErpJson.str("openBalance=sum of current open lines; historical as-of requires event replay (not claimed here)");
        out = out + "}";
        return out;
    }

    static void seedBankBalance(long companyId, long amount) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(amount));
        p.add(CryptoParse.longToString(companyId));
        ErpDb.exec("UPDATE bank_accounts SET balance=? WHERE company_id=?", p);
    }
}

class CreateOpenArWork implements DbTransactionWork {
    long companyId;
    long customerId;
    long totalAmount;
    long invoiceDate;
    long dueDate;
    String currency;

    public JnFuture run(DbConnection conn) {
        long now = AsyncClockNative.wallMillis();
        long seq = ErpDb.nextDocNoConn(conn, companyId, "AR");
        String no = "AR-S-" + CryptoParse.longToString(seq);
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(CryptoParse.longToString(customerId));
        p.add(no);
        p.add(CryptoParse.longToString(invoiceDate));
        p.add(CryptoParse.longToString(dueDate));
        p.add(currency);
        p.add(CryptoParse.longToString(totalAmount));
        p.add(CryptoParse.longToString(totalAmount));
        p.add(InvoiceStatus.POSTED);
        p.add(SettlementStatus.OPEN);
        p.add(CryptoParse.longToString(now));
        p.add(currency);
        long id = ErpDb.insertReturningIdConn(conn,
                "INSERT INTO ar_invoices(company_id, customer_id, invoice_number, invoice_date, due_date, currency, total_amount, paid_amount, open_amount, status, settlement_status, source_delivery_id, created_at, is_provisional, adjusted_amount, currency_code) VALUES (?,?,?,?,?,?,?,0,?,?,?,0,?,0,0,?)",
                p);
        PostResultDto ok = ErpFinanceSettlement.okDoc("OK", "AR created",
                "{\"id\":" + ErpJson.num(id) + "}");
        return JnFuture.completed(Erp.loop, ok);
    }
}

class CreateOpenApWork implements DbTransactionWork {
    long companyId;
    long supplierId;
    long totalAmount;
    long invoiceDate;
    long dueDate;
    String currency;

    public JnFuture run(DbConnection conn) {
        long now = AsyncClockNative.wallMillis();
        long seq = ErpDb.nextDocNoConn(conn, companyId, "AP");
        String no = "AP-S-" + CryptoParse.longToString(seq);
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(CryptoParse.longToString(supplierId));
        p.add(no);
        p.add(CryptoParse.longToString(invoiceDate));
        p.add(CryptoParse.longToString(dueDate));
        p.add(currency);
        p.add(CryptoParse.longToString(totalAmount));
        p.add(CryptoParse.longToString(totalAmount));
        p.add(InvoiceStatus.POSTED);
        p.add(SettlementStatus.OPEN);
        p.add(CryptoParse.longToString(now));
        p.add(currency);
        long id = ErpDb.insertReturningIdConn(conn,
                "INSERT INTO ap_invoices(company_id, supplier_id, invoice_number, invoice_date, due_date, currency, total_amount, paid_amount, open_amount, status, settlement_status, source_gr_id, created_at, is_provisional, adjusted_amount, currency_code) VALUES (?,?,?,?,?,?,?,0,?,?,?,0,?,0,0,?)",
                p);
        PostResultDto ok = ErpFinanceSettlement.okDoc("OK", "AP created",
                "{\"id\":" + ErpJson.num(id) + "}");
        return JnFuture.completed(Erp.loop, ok);
    }
}

class ReceiptCreateWork implements DbTransactionWork {
    long companyId;
    long userId;
    IncomingPaymentRequest req;

    public JnFuture run(DbConnection conn) {
        long now = AsyncClockNative.wallMillis();
        String cached = ErpPosting.cachedIdempotencyConn(conn, companyId, req.idempotencyKey);
        if (cached != null && cached.length() > 0) {
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
        ErpFinanceSettlement.sortAllocByInvoiceId(req.allocations);
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
        int doPost = req.postNow;
        if (req.allocations != null && req.allocations.size() > 0) {
            doPost = 1;
        }
        String status = PaymentStatus.DRAFT;
        if (doPost != 0) {
            status = PaymentStatus.POSTED;
        }
        long seq = ErpDb.nextDocNoConn(conn, companyId, "IP");
        String docNo = "IP-" + CryptoParse.longToString(seq);
        long unalloc = req.amount;
        if (doPost != 0) {
            unalloc = req.amount - allocTotal;
        } else {
            allocTotal = 0L;
            unalloc = req.amount;
        }
        String method = req.paymentMethod == null ? "" : req.paymentMethod;
        String xref = req.externalRef == null ? "" : req.externalRef;
        String notes = req.notes == null ? "" : req.notes;
        ArrayList<Object> hdr = new ArrayList<Object>();
        hdr.add(CryptoParse.longToString(companyId));
        hdr.add(CryptoParse.longToString(req.customerId));
        hdr.add(CryptoParse.longToString(req.paymentDate));
        hdr.add(CryptoParse.longToString(req.amount));
        hdr.add(CryptoParse.longToString(allocTotal));
        hdr.add(CryptoParse.longToString(unalloc));
        hdr.add(CryptoParse.longToString(req.bankAccountId));
        hdr.add(status);
        hdr.add(req.idempotencyKey);
        hdr.add(CryptoParse.longToString(now));
        hdr.add(method);
        hdr.add(xref);
        hdr.add(notes);
        hdr.add(docNo);
        hdr.add(CryptoParse.longToString(userId));
        long payId = ErpDb.insertReturningIdConn(conn,
                "INSERT INTO incoming_payments(company_id, customer_id, payment_date, amount, allocated_amount, unallocated_amount, bank_account_id, status, idempotency_key, created_at, payment_method, external_ref, notes, doc_no, created_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                hdr);
        if (doPost != 0) {
            ErpFinanceSettlement.applyBankMovementConn(conn, companyId, req.bankAccountId, req.amount,
                    CashMovementType.RECEIPT, req.paymentDate, "INCOMING_PAYMENT", payId, req.customerId, userId, now,
                    "bcm-ip-" + CryptoParse.longToString(payId), notes);
            ErpFinanceSettlement.postIncomingPaymentJournalConn(conn, companyId, userId, payId, bankGl, req.amount,
                    req.customerId, req.paymentDate);
            ErpDb.execConn(conn,
                    "UPDATE incoming_payments SET posted_at=" + CryptoParse.longToString(now)
                            + ", posted_by=" + CryptoParse.longToString(userId)
                            + " WHERE id=" + CryptoParse.longToString(payId),
                    new ArrayList<Object>());
            if (req.allocations != null) {
                int ai2 = 0;
                while (ai2 < req.allocations.size()) {
                    PaymentAllocationInput a = (PaymentAllocationInput) req.allocations.get(ai2);
                    String ik = req.idempotencyKey + "-a" + CryptoParse.longToString(a.invoiceId);
                    ErpFinanceSettlement.applyArAllocConn(conn, companyId, payId, req.customerId, a.invoiceId,
                            a.allocatedAmount, userId, now, ik, "");
                    ai2 = ai2 + 1;
                }
            }
            ErpFinanceSettlement.recordSourceEventConn(conn, companyId, "INCOMING_PAYMENT", payId,
                    FinanceEventType.RECEIPT_POST, payId, now);
        }
        String docJson = "{\"id\":" + ErpJson.num(payId) + ",\"docNo\":" + ErpJson.str(docNo)
                + ",\"amount\":" + ErpJson.num(req.amount)
                + ",\"unallocated\":" + ErpJson.num(unalloc)
                + ",\"status\":" + ErpJson.str(status) + "}";
        PostResultDto ok = ErpFinanceSettlement.okDoc("OK", "payment posted", docJson);
        ErpPosting.storeIdempotencyConn(conn, companyId, req.idempotencyKey, "IP", docJson, now);
        return JnFuture.completed(Erp.loop, ok);
    }
}

class ReceiptPostWork implements DbTransactionWork {
    long companyId;
    long userId;
    long paymentId;
    String idempotencyKey;

    public JnFuture run(DbConnection conn) {
        long now = AsyncClockNative.wallMillis();
        if (idempotencyKey != null && idempotencyKey.length() > 0) {
            String cached = ErpPosting.cachedIdempotencyConn(conn, companyId, idempotencyKey);
            if (cached != null && cached.length() > 0) {
                return JnFuture.completed(Erp.loop, ErpPosting.resultFromJson(cached));
            }
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(paymentId));
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult r = ErpDb.queryConn(conn,
                "SELECT status, amount, bank_account_id, customer_id, payment_date, unallocated_amount FROM incoming_payments WHERE id=? AND company_id=?",
                p);
        if (r.rows.size() == 0) {
            throw new TransactionException("receipt not found");
        }
        String st = r.rows.get(0).getString(0);
        long amount = r.rows.get(0).getLong(1);
        long bankId = r.rows.get(0).getLong(2);
        long cust = r.rows.get(0).getLong(3);
        long pdate = r.rows.get(0).getLong(4);
        long unalloc = r.rows.get(0).getLong(5);
        if (st.equals(PaymentStatus.POSTED)) {
            PostResultDto replay = ErpFinanceSettlement.okDoc("ALREADY_POSTED", "already posted",
                    "{\"id\":" + ErpJson.num(paymentId) + "}");
            return JnFuture.completed(Erp.loop, replay);
        }
        if (st.equals(PaymentStatus.DRAFT) == false) {
            throw new TransactionException("receipt not draft");
        }
        long bankGl = ErpFinanceArAp.bankLedgerAccountConn(conn, companyId, bankId);
        if (bankGl == 0L) {
            throw new TransactionException("bank account invalid");
        }
        ErpFinanceSettlement.applyBankMovementConn(conn, companyId, bankId, amount, CashMovementType.RECEIPT, pdate,
                "INCOMING_PAYMENT", paymentId, cust, userId, now, "bcm-ip-" + CryptoParse.longToString(paymentId), "");
        ErpFinanceSettlement.postIncomingPaymentJournalConn(conn, companyId, userId, paymentId, bankGl, amount, cust,
                pdate);
        ErpDb.execConn(conn,
                "UPDATE incoming_payments SET status='" + PaymentStatus.POSTED + "', posted_at="
                        + CryptoParse.longToString(now) + ", posted_by=" + CryptoParse.longToString(userId)
                        + ", unallocated_amount=" + CryptoParse.longToString(amount)
                        + ", allocated_amount=0 WHERE id=" + CryptoParse.longToString(paymentId)
                        + " AND company_id=" + CryptoParse.longToString(companyId)
                        + " AND status='" + PaymentStatus.DRAFT + "'",
                new ArrayList<Object>());
        ErpFinanceSettlement.recordSourceEventConn(conn, companyId, "INCOMING_PAYMENT", paymentId,
                FinanceEventType.RECEIPT_POST, paymentId, now);
        String docJson = "{\"id\":" + ErpJson.num(paymentId) + ",\"unallocated\":" + ErpJson.num(amount) + "}";
        PostResultDto ok = ErpFinanceSettlement.okDoc("OK", "receipt posted", docJson);
        if (idempotencyKey != null && idempotencyKey.length() > 0) {
            ErpPosting.storeIdempotencyConn(conn, companyId, idempotencyKey, "IP_POST", docJson, now);
        }
        return JnFuture.completed(Erp.loop, ok);
    }
}

class ReceiptAllocWork implements DbTransactionWork {
    long companyId;
    long userId;
    SettlementAllocateRequest req;

    public JnFuture run(DbConnection conn) {
        long now = AsyncClockNative.wallMillis();
        String cached = ErpPosting.cachedIdempotencyConn(conn, companyId, req.idempotencyKey);
        if (cached != null && cached.length() > 0) {
            return JnFuture.completed(Erp.loop, ErpPosting.resultFromJson(cached));
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(req.paymentId));
        p.add(CryptoParse.longToString(companyId));
        String lockPay = "SELECT status, amount, allocated_amount, unallocated_amount, customer_id FROM incoming_payments WHERE id=? AND company_id=?";
        if (Erp.dialect.equals("postgresql")) {
            lockPay = lockPay + " FOR UPDATE";
        }
        DbQueryResult pr = ErpDb.queryConn(conn, lockPay, p);
        if (pr.rows.size() == 0) {
            throw new TransactionException("receipt not found");
        }
        String st = pr.rows.get(0).getString(0);
        long amount = pr.rows.get(0).getLong(1);
        long allocated = pr.rows.get(0).getLong(2);
        long unalloc = pr.rows.get(0).getLong(3);
        long cust = pr.rows.get(0).getLong(4);
        if (st.equals(PaymentStatus.POSTED) == false) {
            throw new TransactionException("receipt not posted");
        }
        ErpFinanceSettlement.sortAllocByInvoiceId(req.allocations);
        long allocTotal = 0L;
        if (req.allocations == null || req.allocations.size() == 0) {
            throw new TransactionException("allocations required");
        }
        int ai = 0;
        while (ai < req.allocations.size()) {
            PaymentAllocationInput a = (PaymentAllocationInput) req.allocations.get(ai);
            allocTotal = allocTotal + a.allocatedAmount;
            ai = ai + 1;
        }
        if (allocTotal > unalloc) {
            throw new TransactionException("allocation exceeds unallocated");
        }
        ai = 0;
        while (ai < req.allocations.size()) {
            PaymentAllocationInput a = (PaymentAllocationInput) req.allocations.get(ai);
            String ik = req.idempotencyKey + "-a" + CryptoParse.longToString(a.invoiceId) + "-"
                    + CryptoParse.longToString(ai);
            ErpFinanceSettlement.applyArAllocConn(conn, companyId, req.paymentId, cust, a.invoiceId,
                    a.allocatedAmount, userId, now, ik, req.reason);
            ai = ai + 1;
        }
        long newAlloc = allocated + allocTotal;
        long newUn = unalloc - allocTotal;
        ErpDb.execConn(conn,
                "UPDATE incoming_payments SET allocated_amount=" + CryptoParse.longToString(newAlloc)
                        + ", unallocated_amount=" + CryptoParse.longToString(newUn)
                        + " WHERE id=" + CryptoParse.longToString(req.paymentId)
                        + " AND company_id=" + CryptoParse.longToString(companyId)
                        + " AND unallocated_amount=" + CryptoParse.longToString(unalloc),
                new ArrayList<Object>());
        String docJson = "{\"paymentId\":" + ErpJson.num(req.paymentId)
                + ",\"allocated\":" + ErpJson.num(newAlloc)
                + ",\"unallocated\":" + ErpJson.num(newUn) + "}";
        PostResultDto ok = ErpFinanceSettlement.okDoc("OK", "allocated", docJson);
        ErpPosting.storeIdempotencyConn(conn, companyId, req.idempotencyKey, "IP_ALLOC", docJson, now);
        return JnFuture.completed(Erp.loop, ok);
    }
}

class ReceiptReverseWork implements DbTransactionWork {
    long companyId;
    long userId;
    SettlementReverseRequest req;

    public JnFuture run(DbConnection conn) {
        long now = AsyncClockNative.wallMillis();
        String cached = ErpPosting.cachedIdempotencyConn(conn, companyId, req.idempotencyKey);
        if (cached != null && cached.length() > 0) {
            return JnFuture.completed(Erp.loop, ErpPosting.resultFromJson(cached));
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(req.allocationId));
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult ar = ErpDb.queryConn(conn,
                "SELECT id, payment_id, invoice_id, allocated_amount, status, reversed_by_id FROM ar_settlement_allocs WHERE id=? AND company_id=?",
                p);
        if (ar.rows.size() == 0) {
            throw new TransactionException("allocation not found");
        }
        long allocId = ar.rows.get(0).getLong(0);
        long paymentId = ar.rows.get(0).getLong(1);
        long invoiceId = ar.rows.get(0).getLong(2);
        long amount = ar.rows.get(0).getLong(3);
        String st = ar.rows.get(0).getString(4);
        long revBy = ar.rows.get(0).getLong(5);
        if (st.equals(AllocationRowStatus.REVERSED) || revBy > 0L) {
            throw new TransactionException("allocation already reversed");
        }
        ArrayList<Object> pp = new ArrayList<Object>();
        pp.add(CryptoParse.longToString(paymentId));
        pp.add(CryptoParse.longToString(companyId));
        DbQueryResult pr = ErpDb.queryConn(conn,
                "SELECT allocated_amount, unallocated_amount, customer_id, status FROM incoming_payments WHERE id=? AND company_id=?",
                pp);
        if (pr.rows.size() == 0) {
            throw new TransactionException("receipt not found");
        }
        long allocated = pr.rows.get(0).getLong(0);
        long unalloc = pr.rows.get(0).getLong(1);
        long cust = pr.rows.get(0).getLong(2);
        String pst = pr.rows.get(0).getString(3);
        if (pst.equals(PaymentStatus.POSTED) == false) {
            throw new TransactionException("receipt not posted");
        }
        String lockSql = "SELECT open_amount, paid_amount, total_amount, adjusted_amount FROM ar_invoices WHERE id="
                + CryptoParse.longToString(invoiceId) + " AND company_id=" + CryptoParse.longToString(companyId);
        if (Erp.dialect.equals("postgresql")) {
            lockSql = lockSql + " FOR UPDATE";
        }
        DbQueryResult ir = ErpDb.queryConn(conn, lockSql, new ArrayList<Object>());
        if (ir.rows.size() == 0) {
            throw new TransactionException("AR not found");
        }
        long open = ir.rows.get(0).getLong(0);
        long paid = ir.rows.get(0).getLong(1);
        long total = ir.rows.get(0).getLong(2);
        long adj = ir.rows.get(0).getLong(3);
        if (amount > paid) {
            throw new TransactionException("reverse exceeds paid");
        }
        long newPaid = paid - amount;
        long newOpen = open + amount;
        long effective = total + adj;
        String settle = ErpFinanceSettlement.settlementOf(newOpen, newPaid, effective);
        ErpDb.execConn(conn,
                "UPDATE ar_invoices SET paid_amount=" + CryptoParse.longToString(newPaid)
                        + ", open_amount=" + CryptoParse.longToString(newOpen)
                        + ", settlement_status='" + settle + "'"
                        + " WHERE id=" + CryptoParse.longToString(invoiceId)
                        + " AND company_id=" + CryptoParse.longToString(companyId)
                        + " AND paid_amount=" + CryptoParse.longToString(paid)
                        + " AND open_amount=" + CryptoParse.longToString(open),
                new ArrayList<Object>());
        String reason = req.reason == null ? "" : req.reason;
        ArrayList<Object> revP = new ArrayList<Object>();
        revP.add(CryptoParse.longToString(companyId));
        revP.add(CryptoParse.longToString(paymentId));
        revP.add(CryptoParse.longToString(invoiceId));
        revP.add(CryptoParse.longToString(amount));
        revP.add(AllocationRowStatus.ACTIVE);
        revP.add(CryptoParse.longToString(allocId));
        revP.add(reason);
        revP.add(CryptoParse.longToString(userId));
        revP.add(CryptoParse.longToString(now));
        revP.add(req.idempotencyKey + "-rev");
        long revId = ErpDb.insertReturningIdConn(conn,
                "INSERT INTO ar_settlement_allocs(company_id, payment_id, invoice_id, allocated_amount, status, reverse_of_id, reason, created_by, created_at, idempotency_key) VALUES (?,?,?,?,?,?,?,?,?,?)",
                revP);
        /* Mark reverse row as REVERSED marker? Keep reverse_of link; mark original REVERSED. */
        ErpDb.execConn(conn,
                "UPDATE ar_settlement_allocs SET status='" + AllocationRowStatus.REVERSED
                        + "', reversed_by_id=" + CryptoParse.longToString(revId)
                        + " WHERE id=" + CryptoParse.longToString(allocId),
                new ArrayList<Object>());
        ErpDb.execConn(conn,
                "UPDATE ar_settlement_allocs SET status='" + AllocationRowStatus.REVERSED
                        + "' WHERE id=" + CryptoParse.longToString(revId),
                new ArrayList<Object>());
        long newAlloc = allocated - amount;
        long newUn = unalloc + amount;
        ErpDb.execConn(conn,
                "UPDATE incoming_payments SET allocated_amount=" + CryptoParse.longToString(newAlloc)
                        + ", unallocated_amount=" + CryptoParse.longToString(newUn)
                        + " WHERE id=" + CryptoParse.longToString(paymentId)
                        + " AND company_id=" + CryptoParse.longToString(companyId)
                        + " AND allocated_amount=" + CryptoParse.longToString(allocated),
                new ArrayList<Object>());
        String docJson = "{\"allocationId\":" + ErpJson.num(allocId)
                + ",\"reverseId\":" + ErpJson.num(revId)
                + ",\"unallocated\":" + ErpJson.num(newUn)
                + ",\"invoiceOpen\":" + ErpJson.num(newOpen) + "}";
        PostResultDto ok = ErpFinanceSettlement.okDoc("OK", "allocation reversed", docJson);
        ErpPosting.storeIdempotencyConn(conn, companyId, req.idempotencyKey, "IP_REV", docJson, now);
        return JnFuture.completed(Erp.loop, ok);
    }
}

class PaymentCreateWork implements DbTransactionWork {
    long companyId;
    long userId;
    OutgoingPaymentRequest req;

    public JnFuture run(DbConnection conn) {
        long now = AsyncClockNative.wallMillis();
        String cached = ErpPosting.cachedIdempotencyConn(conn, companyId, req.idempotencyKey);
        if (cached != null && cached.length() > 0) {
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
        ErpFinanceSettlement.sortAllocByInvoiceId(req.allocations);
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
        int doPost = req.postNow;
        if (req.allocations != null && req.allocations.size() > 0) {
            doPost = 1;
        }
        String status = PaymentStatus.DRAFT;
        if (doPost != 0) {
            status = PaymentStatus.POSTED;
        }
        long seq = ErpDb.nextDocNoConn(conn, companyId, "OP");
        String docNo = "OP-" + CryptoParse.longToString(seq);
        long unalloc = req.amount;
        if (doPost != 0) {
            unalloc = req.amount - allocTotal;
        } else {
            allocTotal = 0L;
        }
        String method = req.paymentMethod == null ? "" : req.paymentMethod;
        String xref = req.externalRef == null ? "" : req.externalRef;
        String notes = req.notes == null ? "" : req.notes;
        ArrayList<Object> hdr = new ArrayList<Object>();
        hdr.add(CryptoParse.longToString(companyId));
        hdr.add(CryptoParse.longToString(req.supplierId));
        hdr.add(CryptoParse.longToString(req.paymentDate));
        hdr.add(CryptoParse.longToString(req.amount));
        hdr.add(CryptoParse.longToString(allocTotal));
        hdr.add(CryptoParse.longToString(unalloc));
        hdr.add(CryptoParse.longToString(req.bankAccountId));
        hdr.add(status);
        hdr.add(req.idempotencyKey);
        hdr.add(CryptoParse.longToString(now));
        hdr.add(method);
        hdr.add(xref);
        hdr.add(notes);
        hdr.add(docNo);
        hdr.add(CryptoParse.longToString(userId));
        long payId = ErpDb.insertReturningIdConn(conn,
                "INSERT INTO outgoing_payments(company_id, supplier_id, payment_date, amount, allocated_amount, unallocated_amount, bank_account_id, status, idempotency_key, created_at, payment_method, external_ref, notes, doc_no, created_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                hdr);
        if (doPost != 0) {
            ErpFinanceSettlement.applyBankMovementConn(conn, companyId, req.bankAccountId, 0L - req.amount,
                    CashMovementType.PAYMENT, req.paymentDate, "OUTGOING_PAYMENT", payId, req.supplierId, userId, now,
                    "bcm-op-" + CryptoParse.longToString(payId), notes);
            ErpFinanceSettlement.postOutgoingPaymentJournalConn(conn, companyId, userId, payId, bankGl, req.amount,
                    req.supplierId, req.paymentDate);
            ErpDb.execConn(conn,
                    "UPDATE outgoing_payments SET posted_at=" + CryptoParse.longToString(now)
                            + ", posted_by=" + CryptoParse.longToString(userId)
                            + " WHERE id=" + CryptoParse.longToString(payId),
                    new ArrayList<Object>());
            if (req.allocations != null) {
                int ai2 = 0;
                while (ai2 < req.allocations.size()) {
                    PaymentAllocationInput a = (PaymentAllocationInput) req.allocations.get(ai2);
                    String ik = req.idempotencyKey + "-a" + CryptoParse.longToString(a.invoiceId);
                    ErpFinanceSettlement.applyApAllocConn(conn, companyId, payId, req.supplierId, a.invoiceId,
                            a.allocatedAmount, userId, now, ik, "");
                    ai2 = ai2 + 1;
                }
            }
            ErpFinanceSettlement.recordSourceEventConn(conn, companyId, "OUTGOING_PAYMENT", payId,
                    FinanceEventType.PAYMENT_POST, payId, now);
        }
        String docJson = "{\"id\":" + ErpJson.num(payId) + ",\"docNo\":" + ErpJson.str(docNo)
                + ",\"amount\":" + ErpJson.num(req.amount)
                + ",\"unallocated\":" + ErpJson.num(unalloc)
                + ",\"status\":" + ErpJson.str(status) + "}";
        PostResultDto ok = ErpFinanceSettlement.okDoc("OK", "payment posted", docJson);
        ErpPosting.storeIdempotencyConn(conn, companyId, req.idempotencyKey, "OP", docJson, now);
        return JnFuture.completed(Erp.loop, ok);
    }
}

class PaymentPostWork implements DbTransactionWork {
    long companyId;
    long userId;
    long paymentId;
    String idempotencyKey;

    public JnFuture run(DbConnection conn) {
        long now = AsyncClockNative.wallMillis();
        if (idempotencyKey != null && idempotencyKey.length() > 0) {
            String cached = ErpPosting.cachedIdempotencyConn(conn, companyId, idempotencyKey);
            if (cached != null && cached.length() > 0) {
                return JnFuture.completed(Erp.loop, ErpPosting.resultFromJson(cached));
            }
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(paymentId));
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult r = ErpDb.queryConn(conn,
                "SELECT status, amount, bank_account_id, supplier_id, payment_date FROM outgoing_payments WHERE id=? AND company_id=?",
                p);
        if (r.rows.size() == 0) {
            throw new TransactionException("payment not found");
        }
        String st = r.rows.get(0).getString(0);
        long amount = r.rows.get(0).getLong(1);
        long bankId = r.rows.get(0).getLong(2);
        long sup = r.rows.get(0).getLong(3);
        long pdate = r.rows.get(0).getLong(4);
        if (st.equals(PaymentStatus.POSTED)) {
            return JnFuture.completed(Erp.loop, ErpFinanceSettlement.okDoc("ALREADY_POSTED", "already posted",
                    "{\"id\":" + ErpJson.num(paymentId) + "}"));
        }
        if (st.equals(PaymentStatus.DRAFT) == false) {
            throw new TransactionException("payment not draft");
        }
        long bankGl = ErpFinanceArAp.bankLedgerAccountConn(conn, companyId, bankId);
        if (bankGl == 0L) {
            throw new TransactionException("bank account invalid");
        }
        ErpFinanceSettlement.applyBankMovementConn(conn, companyId, bankId, 0L - amount, CashMovementType.PAYMENT,
                pdate, "OUTGOING_PAYMENT", paymentId, sup, userId, now, "bcm-op-" + CryptoParse.longToString(paymentId),
                "");
        ErpFinanceSettlement.postOutgoingPaymentJournalConn(conn, companyId, userId, paymentId, bankGl, amount, sup,
                pdate);
        ErpDb.execConn(conn,
                "UPDATE outgoing_payments SET status='" + PaymentStatus.POSTED + "', posted_at="
                        + CryptoParse.longToString(now) + ", posted_by=" + CryptoParse.longToString(userId)
                        + ", unallocated_amount=" + CryptoParse.longToString(amount)
                        + ", allocated_amount=0 WHERE id=" + CryptoParse.longToString(paymentId)
                        + " AND status='" + PaymentStatus.DRAFT + "'",
                new ArrayList<Object>());
        ErpFinanceSettlement.recordSourceEventConn(conn, companyId, "OUTGOING_PAYMENT", paymentId,
                FinanceEventType.PAYMENT_POST, paymentId, now);
        String docJson = "{\"id\":" + ErpJson.num(paymentId) + ",\"unallocated\":" + ErpJson.num(amount) + "}";
        PostResultDto ok = ErpFinanceSettlement.okDoc("OK", "payment posted", docJson);
        if (idempotencyKey != null && idempotencyKey.length() > 0) {
            ErpPosting.storeIdempotencyConn(conn, companyId, idempotencyKey, "OP_POST", docJson, now);
        }
        return JnFuture.completed(Erp.loop, ok);
    }
}

class PaymentAllocWork implements DbTransactionWork {
    long companyId;
    long userId;
    SettlementAllocateRequest req;

    public JnFuture run(DbConnection conn) {
        long now = AsyncClockNative.wallMillis();
        String cached = ErpPosting.cachedIdempotencyConn(conn, companyId, req.idempotencyKey);
        if (cached != null && cached.length() > 0) {
            return JnFuture.completed(Erp.loop, ErpPosting.resultFromJson(cached));
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(req.paymentId));
        p.add(CryptoParse.longToString(companyId));
        String lockPay = "SELECT status, allocated_amount, unallocated_amount, supplier_id FROM outgoing_payments WHERE id=? AND company_id=?";
        if (Erp.dialect.equals("postgresql")) {
            lockPay = lockPay + " FOR UPDATE";
        }
        DbQueryResult pr = ErpDb.queryConn(conn, lockPay, p);
        if (pr.rows.size() == 0) {
            throw new TransactionException("payment not found");
        }
        String st = pr.rows.get(0).getString(0);
        long allocated = pr.rows.get(0).getLong(1);
        long unalloc = pr.rows.get(0).getLong(2);
        long sup = pr.rows.get(0).getLong(3);
        if (st.equals(PaymentStatus.POSTED) == false) {
            throw new TransactionException("payment not posted");
        }
        ErpFinanceSettlement.sortAllocByInvoiceId(req.allocations);
        long allocTotal = 0L;
        int ai = 0;
        while (ai < req.allocations.size()) {
            PaymentAllocationInput a = (PaymentAllocationInput) req.allocations.get(ai);
            allocTotal = allocTotal + a.allocatedAmount;
            ai = ai + 1;
        }
        if (allocTotal > unalloc) {
            throw new TransactionException("allocation exceeds unallocated");
        }
        ai = 0;
        while (ai < req.allocations.size()) {
            PaymentAllocationInput a = (PaymentAllocationInput) req.allocations.get(ai);
            String ik = req.idempotencyKey + "-a" + CryptoParse.longToString(a.invoiceId) + "-"
                    + CryptoParse.longToString(ai);
            ErpFinanceSettlement.applyApAllocConn(conn, companyId, req.paymentId, sup, a.invoiceId,
                    a.allocatedAmount, userId, now, ik, req.reason);
            ai = ai + 1;
        }
        long newAlloc = allocated + allocTotal;
        long newUn = unalloc - allocTotal;
        ErpDb.execConn(conn,
                "UPDATE outgoing_payments SET allocated_amount=" + CryptoParse.longToString(newAlloc)
                        + ", unallocated_amount=" + CryptoParse.longToString(newUn)
                        + " WHERE id=" + CryptoParse.longToString(req.paymentId)
                        + " AND unallocated_amount=" + CryptoParse.longToString(unalloc),
                new ArrayList<Object>());
        String docJson = "{\"paymentId\":" + ErpJson.num(req.paymentId)
                + ",\"allocated\":" + ErpJson.num(newAlloc)
                + ",\"unallocated\":" + ErpJson.num(newUn) + "}";
        PostResultDto ok = ErpFinanceSettlement.okDoc("OK", "allocated", docJson);
        ErpPosting.storeIdempotencyConn(conn, companyId, req.idempotencyKey, "OP_ALLOC", docJson, now);
        return JnFuture.completed(Erp.loop, ok);
    }
}

class PaymentReverseWork implements DbTransactionWork {
    long companyId;
    long userId;
    SettlementReverseRequest req;

    public JnFuture run(DbConnection conn) {
        long now = AsyncClockNative.wallMillis();
        String cached = ErpPosting.cachedIdempotencyConn(conn, companyId, req.idempotencyKey);
        if (cached != null && cached.length() > 0) {
            return JnFuture.completed(Erp.loop, ErpPosting.resultFromJson(cached));
        }
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(req.allocationId));
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult ar = ErpDb.queryConn(conn,
                "SELECT id, payment_id, invoice_id, allocated_amount, status, reversed_by_id FROM ap_settlement_allocs WHERE id=? AND company_id=?",
                p);
        if (ar.rows.size() == 0) {
            throw new TransactionException("allocation not found");
        }
        long allocId = ar.rows.get(0).getLong(0);
        long paymentId = ar.rows.get(0).getLong(1);
        long invoiceId = ar.rows.get(0).getLong(2);
        long amount = ar.rows.get(0).getLong(3);
        String st = ar.rows.get(0).getString(4);
        long revBy = ar.rows.get(0).getLong(5);
        if (st.equals(AllocationRowStatus.REVERSED) || revBy > 0L) {
            throw new TransactionException("allocation already reversed");
        }
        ArrayList<Object> pp = new ArrayList<Object>();
        pp.add(CryptoParse.longToString(paymentId));
        pp.add(CryptoParse.longToString(companyId));
        DbQueryResult pr = ErpDb.queryConn(conn,
                "SELECT allocated_amount, unallocated_amount, status FROM outgoing_payments WHERE id=? AND company_id=?",
                pp);
        long allocated = pr.rows.get(0).getLong(0);
        long unalloc = pr.rows.get(0).getLong(1);
        String pst = pr.rows.get(0).getString(2);
        if (pst.equals(PaymentStatus.POSTED) == false) {
            throw new TransactionException("payment not posted");
        }
        String lockSql = "SELECT open_amount, paid_amount, total_amount, adjusted_amount FROM ap_invoices WHERE id="
                + CryptoParse.longToString(invoiceId) + " AND company_id=" + CryptoParse.longToString(companyId);
        if (Erp.dialect.equals("postgresql")) {
            lockSql = lockSql + " FOR UPDATE";
        }
        DbQueryResult ir = ErpDb.queryConn(conn, lockSql, new ArrayList<Object>());
        long open = ir.rows.get(0).getLong(0);
        long paid = ir.rows.get(0).getLong(1);
        long total = ir.rows.get(0).getLong(2);
        long adj = ir.rows.get(0).getLong(3);
        long newPaid = paid - amount;
        long newOpen = open + amount;
        String settle = ErpFinanceSettlement.settlementOf(newOpen, newPaid, total + adj);
        ErpDb.execConn(conn,
                "UPDATE ap_invoices SET paid_amount=" + CryptoParse.longToString(newPaid)
                        + ", open_amount=" + CryptoParse.longToString(newOpen)
                        + ", settlement_status='" + settle + "'"
                        + " WHERE id=" + CryptoParse.longToString(invoiceId)
                        + " AND paid_amount=" + CryptoParse.longToString(paid)
                        + " AND open_amount=" + CryptoParse.longToString(open),
                new ArrayList<Object>());
        String reason = req.reason == null ? "" : req.reason;
        ArrayList<Object> revP = new ArrayList<Object>();
        revP.add(CryptoParse.longToString(companyId));
        revP.add(CryptoParse.longToString(paymentId));
        revP.add(CryptoParse.longToString(invoiceId));
        revP.add(CryptoParse.longToString(amount));
        revP.add(AllocationRowStatus.REVERSED);
        revP.add(CryptoParse.longToString(allocId));
        revP.add(reason);
        revP.add(CryptoParse.longToString(userId));
        revP.add(CryptoParse.longToString(now));
        revP.add(req.idempotencyKey + "-rev");
        long revId = ErpDb.insertReturningIdConn(conn,
                "INSERT INTO ap_settlement_allocs(company_id, payment_id, invoice_id, allocated_amount, status, reverse_of_id, reason, created_by, created_at, idempotency_key) VALUES (?,?,?,?,?,?,?,?,?,?)",
                revP);
        ErpDb.execConn(conn,
                "UPDATE ap_settlement_allocs SET status='" + AllocationRowStatus.REVERSED
                        + "', reversed_by_id=" + CryptoParse.longToString(revId)
                        + " WHERE id=" + CryptoParse.longToString(allocId),
                new ArrayList<Object>());
        long newAlloc = allocated - amount;
        long newUn = unalloc + amount;
        ErpDb.execConn(conn,
                "UPDATE outgoing_payments SET allocated_amount=" + CryptoParse.longToString(newAlloc)
                        + ", unallocated_amount=" + CryptoParse.longToString(newUn)
                        + " WHERE id=" + CryptoParse.longToString(paymentId)
                        + " AND allocated_amount=" + CryptoParse.longToString(allocated),
                new ArrayList<Object>());
        String docJson = "{\"allocationId\":" + ErpJson.num(allocId) + ",\"reverseId\":" + ErpJson.num(revId)
                + ",\"unallocated\":" + ErpJson.num(newUn) + "}";
        PostResultDto ok = ErpFinanceSettlement.okDoc("OK", "allocation reversed", docJson);
        ErpPosting.storeIdempotencyConn(conn, companyId, req.idempotencyKey, "OP_REV", docJson, now);
        return JnFuture.completed(Erp.loop, ok);
    }
}

class ReceiptVoidWork implements DbTransactionWork {
    long companyId;
    long userId;
    PaymentVoidRequest req;

    public JnFuture run(DbConnection conn) {
        long now = AsyncClockNative.wallMillis();
        String cached = ErpPosting.cachedIdempotencyConn(conn, companyId, req.idempotencyKey);
        if (cached != null && cached.length() > 0) {
            return JnFuture.completed(Erp.loop, ErpPosting.resultFromJson(cached));
        }
        long payId = req.paymentId;
        ArrayList<Object> pp = new ArrayList<Object>();
        pp.add(CryptoParse.longToString(payId));
        pp.add(CryptoParse.longToString(companyId));
        String lockSql = "SELECT status, amount, bank_account_id, customer_id, payment_date, COALESCE(voided_at,0) FROM incoming_payments WHERE id=? AND company_id=?";
        if (Erp.dialect.equals("postgresql")) {
            lockSql = lockSql + " FOR UPDATE";
        }
        DbQueryResult pr = ErpDb.queryConn(conn, lockSql, pp);
        if (pr.rows.size() == 0) {
            throw new TransactionException("receipt not found");
        }
        String status = pr.rows.get(0).getString(0);
        long amount = pr.rows.get(0).getLong(1);
        long bankId = pr.rows.get(0).getLong(2);
        long customerId = pr.rows.get(0).getLong(3);
        long paymentDate = pr.rows.get(0).getLong(4);
        long voidedAt = pr.rows.get(0).getLong(5);
        if (status.equals(PaymentStatus.CANCELLED) || voidedAt > 0L) {
            throw new TransactionException("receipt already voided");
        }
        if (status.equals(PaymentStatus.POSTED) == false) {
            throw new TransactionException("only POSTED receipts can be voided; status=" + status);
        }
        if (ErpFinanceCore.assertOpenPeriodConn(conn, companyId, paymentDate) == 0) {
            throw new TransactionException("PERIOD_CLOSED:fiscal period closed; cannot void");
        }
        ArrayList<Object> ap = new ArrayList<Object>();
        ap.add(CryptoParse.longToString(companyId));
        ap.add(CryptoParse.longToString(payId));
        DbQueryResult allocs = ErpDb.queryConn(conn,
                "SELECT id FROM ar_settlement_allocs WHERE company_id=? AND payment_id=? AND status='ACTIVE' AND reverse_of_id=0 AND COALESCE(reversed_by_id,0)=0 ORDER BY id",
                ap);
        int ai = 0;
        while (ai < allocs.rows.size()) {
            long allocId = allocs.rows.get(ai).getLong(0);
            ErpFinanceSettlement.reverseActiveArAllocConn(conn, companyId, userId, allocId, now, req.reason,
                    req.idempotencyKey);
            ai = ai + 1;
        }
        long movId = ErpFinanceSettlement.findOriginalBankMovementIdConn(conn, companyId, "INCOMING_PAYMENT", payId);
        if (movId <= 0L) {
            throw new TransactionException("original bank movement missing");
        }
        ArrayList<Object> rvChk = new ArrayList<Object>();
        rvChk.add(CryptoParse.longToString(companyId));
        rvChk.add(CryptoParse.longToString(movId));
        Object alreadyRev = ErpDb.queryOneConn(conn,
                "SELECT COUNT(*) FROM bank_cash_movements WHERE company_id=? AND reverse_of_id=?", rvChk,
                new LongMapper());
        if (ErpNum.asLong(alreadyRev) > 0L) {
            throw new TransactionException("bank movement already reversed");
        }
        String reason = req.reason == null ? "" : req.reason;
        ErpFinanceSettlement.applyBankMovementConn(conn, companyId, bankId, 0L - amount, CashMovementType.REFUND_OUT,
                paymentDate, "INCOMING_PAYMENT_VOID", payId, customerId, userId, now,
                "bcm-void-ip-" + CryptoParse.longToString(payId), reason, movId);
        long jeId = ErpFinanceSettlement.findPaymentJournalIdConn(conn, companyId, JournalSource.INCOMING_PAYMENT,
                payId);
        if (jeId <= 0L) {
            throw new TransactionException("original payment journal missing");
        }
        PostResultDto jeRev = ErpFinanceCore.reverseJournalConn(conn, companyId, userId, jeId, reason,
                "je-void-ip-" + CryptoParse.longToString(payId));
        if (jeRev == null || jeRev.ok == 0) {
            String msg = "payment GL reverse failed";
            if (jeRev != null && jeRev.message != null && jeRev.message.length() > 0) {
                msg = jeRev.message;
            }
            throw new TransactionException(msg);
        }
        ErpDb.execConn(conn,
                "UPDATE incoming_payments SET status='" + PaymentStatus.CANCELLED + "'"
                        + ", voided_at=" + CryptoParse.longToString(now)
                        + ", voided_by=" + CryptoParse.longToString(userId)
                        + ", void_reason='" + ErpPlatformMeta.escapeLite(reason) + "'"
                        + ", allocated_amount=0, unallocated_amount=0"
                        + " WHERE id=" + CryptoParse.longToString(payId)
                        + " AND company_id=" + CryptoParse.longToString(companyId)
                        + " AND status='" + PaymentStatus.POSTED + "'",
                new ArrayList<Object>());
        ArrayList<Object> stP = new ArrayList<Object>();
        stP.add(CryptoParse.longToString(payId));
        stP.add(CryptoParse.longToString(companyId));
        Object stNow = ErpDb.queryOneConn(conn,
                "SELECT status FROM incoming_payments WHERE id=? AND company_id=?", stP, new StringMapper());
        if (stNow == null || ((String) stNow).equals(PaymentStatus.CANCELLED) == false) {
            throw new TransactionException("concurrent void conflict");
        }
        ErpFinanceSettlement.recordSourceEventConn(conn, companyId, "INCOMING_PAYMENT", payId,
                FinanceEventType.RECEIPT_VOID, payId, now);
        String docJson = "{\"id\":" + ErpJson.num(payId)
                + ",\"status\":" + ErpJson.str(PaymentStatus.CANCELLED)
                + ",\"voidedAt\":" + ErpJson.num(now)
                + ",\"bankMovementReverseOf\":" + ErpJson.num(movId)
                + ",\"journalId\":" + ErpJson.num(jeId) + "}";
        PostResultDto ok = ErpFinanceSettlement.okDoc("OK", "receipt voided", docJson);
        ErpPosting.storeIdempotencyConn(conn, companyId, req.idempotencyKey, "IP_VOID", docJson, now);
        return JnFuture.completed(Erp.loop, ok);
    }
}

class PaymentVoidWork implements DbTransactionWork {
    long companyId;
    long userId;
    PaymentVoidRequest req;

    public JnFuture run(DbConnection conn) {
        long now = AsyncClockNative.wallMillis();
        String cached = ErpPosting.cachedIdempotencyConn(conn, companyId, req.idempotencyKey);
        if (cached != null && cached.length() > 0) {
            return JnFuture.completed(Erp.loop, ErpPosting.resultFromJson(cached));
        }
        long payId = req.paymentId;
        ArrayList<Object> pp = new ArrayList<Object>();
        pp.add(CryptoParse.longToString(payId));
        pp.add(CryptoParse.longToString(companyId));
        String lockSql = "SELECT status, amount, bank_account_id, supplier_id, payment_date, COALESCE(voided_at,0) FROM outgoing_payments WHERE id=? AND company_id=?";
        if (Erp.dialect.equals("postgresql")) {
            lockSql = lockSql + " FOR UPDATE";
        }
        DbQueryResult pr = ErpDb.queryConn(conn, lockSql, pp);
        if (pr.rows.size() == 0) {
            throw new TransactionException("payment not found");
        }
        String status = pr.rows.get(0).getString(0);
        long amount = pr.rows.get(0).getLong(1);
        long bankId = pr.rows.get(0).getLong(2);
        long supplierId = pr.rows.get(0).getLong(3);
        long paymentDate = pr.rows.get(0).getLong(4);
        long voidedAt = pr.rows.get(0).getLong(5);
        if (status.equals(PaymentStatus.CANCELLED) || voidedAt > 0L) {
            throw new TransactionException("payment already voided");
        }
        if (status.equals(PaymentStatus.POSTED) == false) {
            throw new TransactionException("only POSTED payments can be voided; status=" + status);
        }
        if (ErpFinanceCore.assertOpenPeriodConn(conn, companyId, paymentDate) == 0) {
            throw new TransactionException("PERIOD_CLOSED:fiscal period closed; cannot void");
        }
        ArrayList<Object> ap = new ArrayList<Object>();
        ap.add(CryptoParse.longToString(companyId));
        ap.add(CryptoParse.longToString(payId));
        DbQueryResult allocs = ErpDb.queryConn(conn,
                "SELECT id FROM ap_settlement_allocs WHERE company_id=? AND payment_id=? AND status='ACTIVE' AND reverse_of_id=0 AND COALESCE(reversed_by_id,0)=0 ORDER BY id",
                ap);
        int ai = 0;
        while (ai < allocs.rows.size()) {
            ErpFinanceSettlement.reverseActiveApAllocConn(conn, companyId, userId, allocs.rows.get(ai).getLong(0),
                    now, req.reason, req.idempotencyKey);
            ai = ai + 1;
        }
        long movId = ErpFinanceSettlement.findOriginalBankMovementIdConn(conn, companyId, "OUTGOING_PAYMENT", payId);
        if (movId <= 0L) {
            throw new TransactionException("original bank movement missing");
        }
        ArrayList<Object> rvChk = new ArrayList<Object>();
        rvChk.add(CryptoParse.longToString(companyId));
        rvChk.add(CryptoParse.longToString(movId));
        if (ErpNum.asLong(ErpDb.queryOneConn(conn,
                "SELECT COUNT(*) FROM bank_cash_movements WHERE company_id=? AND reverse_of_id=?", rvChk,
                new LongMapper())) > 0L) {
            throw new TransactionException("bank movement already reversed");
        }
        String reason = req.reason == null ? "" : req.reason;
        /* Original OP was -amount; void restores +amount. */
        ErpFinanceSettlement.applyBankMovementConn(conn, companyId, bankId, amount, CashMovementType.REFUND_IN,
                paymentDate, "OUTGOING_PAYMENT_VOID", payId, supplierId, userId, now,
                "bcm-void-op-" + CryptoParse.longToString(payId), reason, movId);
        long jeId = ErpFinanceSettlement.findPaymentJournalIdConn(conn, companyId, JournalSource.OUTGOING_PAYMENT,
                payId);
        if (jeId <= 0L) {
            throw new TransactionException("original payment journal missing");
        }
        PostResultDto jeRev = ErpFinanceCore.reverseJournalConn(conn, companyId, userId, jeId, reason,
                "je-void-op-" + CryptoParse.longToString(payId));
        if (jeRev == null || jeRev.ok == 0) {
            String msg = "payment GL reverse failed";
            if (jeRev != null && jeRev.message != null && jeRev.message.length() > 0) {
                msg = jeRev.message;
            }
            throw new TransactionException(msg);
        }
        ErpDb.execConn(conn,
                "UPDATE outgoing_payments SET status='" + PaymentStatus.CANCELLED + "'"
                        + ", voided_at=" + CryptoParse.longToString(now)
                        + ", voided_by=" + CryptoParse.longToString(userId)
                        + ", void_reason='" + ErpPlatformMeta.escapeLite(reason) + "'"
                        + ", allocated_amount=0, unallocated_amount=0"
                        + " WHERE id=" + CryptoParse.longToString(payId)
                        + " AND company_id=" + CryptoParse.longToString(companyId)
                        + " AND status='" + PaymentStatus.POSTED + "'",
                new ArrayList<Object>());
        ArrayList<Object> stP = new ArrayList<Object>();
        stP.add(CryptoParse.longToString(payId));
        stP.add(CryptoParse.longToString(companyId));
        Object stNow = ErpDb.queryOneConn(conn,
                "SELECT status FROM outgoing_payments WHERE id=? AND company_id=?", stP, new StringMapper());
        if (stNow == null || ((String) stNow).equals(PaymentStatus.CANCELLED) == false) {
            throw new TransactionException("concurrent void conflict");
        }
        ErpFinanceSettlement.recordSourceEventConn(conn, companyId, "OUTGOING_PAYMENT", payId,
                FinanceEventType.PAYMENT_VOID, payId, now);
        String docJson = "{\"id\":" + ErpJson.num(payId)
                + ",\"status\":" + ErpJson.str(PaymentStatus.CANCELLED)
                + ",\"voidedAt\":" + ErpJson.num(now)
                + ",\"bankMovementReverseOf\":" + ErpJson.num(movId)
                + ",\"journalId\":" + ErpJson.num(jeId) + "}";
        PostResultDto ok = ErpFinanceSettlement.okDoc("OK", "payment voided", docJson);
        ErpPosting.storeIdempotencyConn(conn, companyId, req.idempotencyKey, "OP_VOID", docJson, now);
        return JnFuture.completed(Erp.loop, ok);
    }
}
