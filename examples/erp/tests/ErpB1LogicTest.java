/**
 * Jnative ERP One phase 1 acceptance: B1 master data, price lists / tax codes / FX, marketing documents
 * (quotation -> order -> delivery -> A/R invoice -> credit memo, and the purchase mirror), GL & sub-ledger.
 * @author 张晓达
 */
class ErpB1LogicTest {
    static long companyId = 0L;
    static long whId = 0L;
    static long goodsId = 0L;
    static long svcId = 0L;
    static long custId = 0L;
    static long suppId = 0L;
    static long asOf = 4102444700000L;
    static int failed = 0;

    static void ok(String name) {
        System.out.println("OK " + name);
    }

    static void bad(String name, String detail) {
        failed = failed + 1;
        System.out.println("FAIL " + name + " " + detail);
    }

    static void eq(String name, long got, long want) {
        if (got == want) {
            ok(name);
        } else {
            bad(name, "got=" + CryptoParse.longToString(got) + " want=" + CryptoParse.longToString(want));
        }
    }

    static void has(String name, String json, String needle) {
        if (json != null && json.indexOf(needle) >= 0) {
            ok(name);
        } else {
            bad(name, "missing " + needle + " in " + json);
        }
    }

    static JsonObject js(String s) {
        return Json.parse(s).asObject();
    }

    static String n(long v) {
        return CryptoParse.longToString(v);
    }

    static int runAll() {
        String code = "B" + n(AsyncClockNative.wallMillis());
        long now = AsyncClockNative.wallMillis();
        ArrayList<Object> co = new ArrayList<Object>();
        co.add(code);
        co.add("B1 Test Co");
        co.add(n(now));
        ErpDb.exec("INSERT INTO companies(code, name, created_at) VALUES (?,?,?)", co);
        ArrayList<Object> chk = new ArrayList<Object>();
        chk.add(code);
        companyId = ErpNum.asLong(ErpDb.queryOne("SELECT id FROM companies WHERE code=?", chk, new LongMapper()));
        ErpFinanceCore.ensureSeed(companyId);
        ArrayList<Object> wh = new ArrayList<Object>();
        wh.add(n(companyId));
        wh.add("WH1");
        wh.add("Main");
        wh.add(n(now));
        ErpDb.exec("INSERT INTO warehouses(company_id, code, name, created_at) VALUES (?,?,?,?)", wh);
        ArrayList<Object> wp = new ArrayList<Object>();
        wp.add(n(companyId));
        whId = ErpNum.asLong(ErpDb.queryOne("SELECT id FROM warehouses WHERE company_id=?", wp, new LongMapper()));
        ErpB1Master.ensureDefaults(companyId);
        try {
            masterScenario();
            purchaseScenario();
            salesScenario();
            ledgerScenario();
        } catch (Exception ex) {
            bad("exception", ErpB1Util.exMsg(ex, "EX_EMPTY"));
        }
        if (failed == 0) {
            System.out.println("PASS b1");
            return 1;
        }
        System.out.println("FAIL b1 failures=" + String.valueOf(failed));
        return 0;
    }

    static long tid(String j) {
        int a = j.indexOf("\"targetId\":");
        String t = j.substring(a + 11, j.length());
        int e = 0;
        while (e < t.length() && t.charAt(e) >= 48 && t.charAt(e) <= 57) {
            e = e + 1;
        }
        return ErpNum.parseLong(t.substring(0, e));
    }

    static long idOfJson(String j) {
        return ErpB1Docs.idOf(j);
    }

    static void masterScenario() {
        eq("default_tax_vat13", ErpNum.asLong(ErpDb.queryOne("SELECT rate_bps FROM tax_codes WHERE company_id=" + n(companyId) + " AND code='VAT13'", new ArrayList<Object>(), new LongMapper())), 1300L);
        eq("default_terms_net30", ErpNum.asLong(ErpDb.queryOne("SELECT net_days FROM payment_terms WHERE company_id=" + n(companyId) + " AND code='NET30'", new ArrayList<Object>(), new LongMapper())), 30L);
        eq("b1_accounts_output_tax", ErpFinanceCore.mappedAccountId(companyId, "OUTPUT_TAX") > 0L ? 1L : 0L, 1L);
        B1Entity tax = ErpB1Master.entity("taxCodes");
        String t3 = ErpB1Master.save(companyId, tax, 0L, js("{\"code\":\"VAT3\",\"name\":\"VAT 3%\",\"rateBps\":300}"));
        has("tax_create", t3, "\"rateBps\":300");
        try {
            ErpB1Master.save(companyId, tax, 0L, js("{\"code\":\"VAT3\",\"name\":\"dup\",\"rateBps\":300}"));
            bad("tax_dup_rejected", "no error");
        } catch (Exception ex) {
            has("tax_dup_rejected", ErpB1Util.exMsg(ex, ""), "DUPLICATE_KEY");
        }
        try {
            ErpB1Master.save(companyId, tax, 0L, js("{\"code\":\"BAD\",\"name\":\"bad\",\"rateBps\":20000}"));
            bad("tax_range_rejected", "no error");
        } catch (Exception ex2) {
            has("tax_range_rejected", ErpB1Util.exMsg(ex2, ""), "rateBps");
        }
        B1Entity items = ErpB1Master.entity("items");
        String g = ErpB1Master.save(companyId, items, 0L, js("{\"sku\":\"B1-G\",\"name\":\"Goods\",\"unitPriceCents\":10000,\"uom\":\"EA\",\"manageMethod\":\"N\"}"));
        goodsId = idOfJson(g);
        String s = ErpB1Master.save(companyId, items, 0L, js("{\"sku\":\"B1-S\",\"name\":\"Install service\",\"unitPriceCents\":5000,\"itemType\":\"SERVICE\"}"));
        svcId = idOfJson(s);
        eq("service_not_inventory", ErpNum.asLong(ErpDb.queryOne("SELECT is_inventory FROM items WHERE id=" + n(svcId), new ArrayList<Object>(), new LongMapper())), 0L);
        B1Entity bps = ErpB1Master.entity("partners");
        String c = ErpB1Master.save(companyId, bps, 0L, js("{\"code\":\"CUS1\",\"name\":\"Customer One\",\"kind\":\"CUSTOMER\",\"discountBps\":500,\"creditLimitCents\":0}"));
        custId = idOfJson(c);
        String sp = ErpB1Master.save(companyId, bps, 0L, js("{\"code\":\"SUP1\",\"name\":\"Supplier One\",\"kind\":\"SUPPLIER\"}"));
        suppId = idOfJson(sp);
        has("bp_discount", ErpB1Master.get(companyId, bps, custId), "\"discountBps\":500");
        try {
            ErpB1Master.save(companyId, bps, 0L, js("{\"code\":\"X\",\"name\":\"x\",\"kind\":\"WRONG\"}"));
            bad("bp_kind_rejected", "no error");
        } catch (Exception ex3) {
            has("bp_kind_rejected", ErpB1Util.exMsg(ex3, ""), "kind");
        }
        long baseList = ErpB1Master.idByCode(companyId, "price_lists", "code", "BASE");
        B1Entity pl = ErpB1Master.entity("priceLists");
        String vip = ErpB1Master.save(companyId, pl, 0L, js("{\"code\":\"VIP\",\"name\":\"VIP 90%\",\"baseListId\":" + n(baseList) + ",\"factorBps\":9000}"));
        long vipId = idOfJson(vip);
        ErpB1Master.save(companyId, ErpB1Master.entity("priceListItems"), 0L, js("{\"priceListId\":" + n(baseList) + ",\"itemId\":" + n(goodsId) + ",\"priceCents\":10000}"));
        ErpB1Master.save(companyId, ErpB1Master.entity("priceListItems"), 0L, js("{\"priceListId\":" + n(baseList) + ",\"itemId\":" + n(goodsId) + ",\"priceCents\":10000}"));
        eq("price_list_item_upsert", ErpNum.asLong(ErpDb.queryOne("SELECT COUNT(*) FROM price_list_items WHERE price_list_id=" + n(baseList), new ArrayList<Object>(), new LongMapper())), 1L);
        ErpB1Master.save(companyId, bps, custId, js("{\"priceListId\":" + n(vipId) + "}"));
        String pr = ErpB1Master.pricingJson(companyId, custId, goodsId, 1);
        has("pricing_vip_price", pr, "\"unitPriceCents\":9000");
        has("pricing_source", pr, "\"source\":\"PRICE_LIST\"");
        has("pricing_partner_discount", pr, "\"discountBps\":500");
        eq("currency_usd_exists", countWhere("SELECT COUNT(*) FROM currencies WHERE company_id=" + n(companyId) + " AND code='USD'"), 1L);
        B1Entity fx = ErpB1Master.entity("exchangeRates");
        ErpB1Master.save(companyId, fx, 0L, js("{\"currencyCode\":\"USD\",\"rateDate\":1000,\"rateMicro\":7100000}"));
        eq("fx_rate_lookup", ErpB1Master.rateMicro(companyId, "USD", AsyncClockNative.wallMillis()), 7100000L);
        eq("fx_local_is_one", ErpB1Master.rateMicro(companyId, "CNY", AsyncClockNative.wallMillis()), 1000000L);
        eq("fx_missing_is_zero", ErpB1Master.rateMicro(companyId, "EUR", AsyncClockNative.wallMillis()), 0L);
        eq("fx_to_local", ErpB1Master.toLocal(10000L, 7100000L), 71000L);
    }

    static long countWhere(String sql) {
        return ErpNum.asLong(ErpDb.queryOne(sql, new ArrayList<Object>(), new LongMapper()));
    }

    static void purchaseScenario() {
        String body = "{\"partnerId\":" + n(suppId) + ",\"warehouseId\":" + n(whId) + ",\"lines\":[{\"itemId\":" + n(goodsId)
                + ",\"quantity\":10,\"unitPriceCents\":4000,\"taxBps\":1300}]}";
        B1Doc d = ErpB1Docs.parseBody(companyId, "PQ", js(body));
        ErpB1Docs.resolve(companyId, d);
        eq("pq_totals_net", d.subtotalCents, 40000L);
        eq("pq_totals_tax", d.taxCents, 5200L);
        eq("pq_totals_total", d.totalCents, 45200L);
        String pq = ErpB1Docs.saveJson(companyId, 0L, d, 0L);
        long pqId = idOfJson(pq);
        ErpB1Docs.submit(companyId, "PQ", pqId);
        eq("pq_open", ErpB1Docs.statusOf(companyId, "PQ", pqId).equals("OPEN") ? 1L : 0L, 1L);
        String cp = ErpB1DocCopy.copy(companyId, 0L, "PO", "PQ", pqId, null);
        has("pq_to_po_target", cp, "\"targetType\":\"PO\"");
        eq("pq_closed_after_copy", ErpB1Docs.statusOf(companyId, "PQ", pqId).equals("CLOSED") ? 1L : 0L, 1L);
        long poId = tid(cp);
        ErpPurchasing.submit(companyId, poId);
        ErpPurchasing.approve(companyId, poId);
        ArrayList<Object> pol = ErpPurchasing.loadPoLines(companyId, poId);
        OrderLineDto pl = (OrderLineDto) pol.get(0);
        PostGoodsReceiptRequest req = new PostGoodsReceiptRequest();
        req.purchaseOrderId = poId;
        req.warehouseId = whId;
        req.idempotencyKey = "b1-gr-" + n(poId);
        req.lines = new ArrayList<Object>();
        PostLineRequest pr = new PostLineRequest();
        pr.orderLineId = pl.id;
        pr.quantity = 10;
        req.lines.add(pr);
        PostResultDto gr = ErpPosting.postGoodsReceipt(companyId, req);
        if (gr == null || gr.ok == 0) {
            bad("gr_post", gr == null ? "null" : gr.message);
            return;
        }
        ErpDb.reconnect();
        long grId = ErpB1Docs.idOf(gr.documentJson);
        eq("clearing_after_gr", 0L - ErpGl.mappedNet(companyId, AccountMapKey.GR_CLEARING, asOf), 40000L);
        /* invoice from GR */
        String inv = ErpB1DocCopy.copy(companyId, 0L, "APINV", "GR", grId, null);
        long invId = idOfJson(inv);
        has("apinv_total", inv, "\"totalCents\":45200");
        try {
            ErpB1DocCopy.copy(companyId, 0L, "APINV", "GR", grId, null);
            bad("apinv_double_copy_rejected", "no error");
        } catch (Exception ex) {
            has("apinv_double_copy_rejected", ErpB1Util.exMsg(ex, ""), "fully consumed");
        }
        String posted = ErpB1DocPost.post(companyId, 1L, "APINV", invId);
        has("apinv_posted", posted, "\"status\":\"POSTED\"");
        eq("ap_control_after_invoice", 0L - ErpGl.mappedNet(companyId, AccountMapKey.AP, asOf), 45200L);
        eq("input_tax_debit", ErpGl.mappedNet(companyId, "INPUT_TAX", asOf), 5200L);
        eq("clearing_cleared", ErpGl.mappedNet(companyId, AccountMapKey.GR_CLEARING, asOf), 0L);
        eq("ap_subledger_converted", countWhere("SELECT COUNT(*) FROM ap_invoices WHERE company_id=" + n(companyId) + " AND source_gr_id=" + n(grId) + " AND is_provisional=0 AND total_amount=45200 AND open_amount=45200 AND status='POSTED'"), 1L);
        String again = ErpB1DocPost.post(companyId, 1L, "APINV", invId);
        has("apinv_post_idempotent", again, "\"status\":\"POSTED\"");
        eq("ap_control_unchanged_on_replay", 0L - ErpGl.mappedNet(companyId, AccountMapKey.AP, asOf), 45200L);
        /* credit memo for 2 units */
        String cmBody = "{\"fromType\":\"APINV\",\"fromId\":" + n(invId) + ",\"lines\":[{\"baseLineId\":" + firstLineId(invId) + ",\"quantity\":2}]}";
        String cm = ErpB1DocCopy.copy(companyId, 0L, "APCM", "APINV", invId, js(cmBody));
        long cmId = idOfJson(cm);
        has("apcm_total", cm, "\"totalCents\":9040");
        ErpB1DocPost.post(companyId, 1L, "APCM", cmId);
        eq("ap_control_after_cm", 0L - ErpGl.mappedNet(companyId, AccountMapKey.AP, asOf), 36160L);
        eq("ap_subledger_after_cm", countWhere("SELECT open_amount FROM ap_invoices WHERE company_id=" + n(companyId) + " AND source_gr_id=" + n(grId)), 36160L);
        eq("input_tax_after_cm", ErpGl.mappedNet(companyId, "INPUT_TAX", asOf), 4160L);
        ErpB1DocPost.reverse(companyId, 1L, "APCM", cmId);
        eq("ap_control_after_cm_reverse", 0L - ErpGl.mappedNet(companyId, AccountMapKey.AP, asOf), 45200L);
        eq("ap_subledger_after_cm_reverse", countWhere("SELECT open_amount FROM ap_invoices WHERE company_id=" + n(companyId) + " AND source_gr_id=" + n(grId)), 45200L);
        /* AP invoice on a stock item without receipt is refused */
        String badBody = "{\"partnerId\":" + n(suppId) + ",\"lines\":[{\"itemId\":" + n(goodsId) + ",\"quantity\":1,\"unitPriceCents\":100}]}";
        B1Doc bd = ErpB1Docs.parseBody(companyId, "APINV", js(badBody));
        ErpB1Docs.resolve(companyId, bd);
        long bdId = idOfJson(ErpB1Docs.saveJson(companyId, 0L, bd, 0L));
        try {
            ErpB1DocPost.post(companyId, 1L, "APINV", bdId);
            bad("apinv_stock_without_gr_rejected", "no error");
        } catch (Exception ex2) {
            has("apinv_stock_without_gr_rejected", ErpB1Util.exMsg(ex2, ""), "INVENTORY_REQUIRES_RECEIPT");
        }
        /* non-stock expense invoice posts to EXPENSE */
        String svcBody = "{\"partnerId\":" + n(suppId) + ",\"lines\":[{\"itemId\":" + n(svcId) + ",\"quantity\":1,\"unitPriceCents\":2000,\"taxBps\":600}]}";
        B1Doc sd = ErpB1Docs.parseBody(companyId, "APINV", js(svcBody));
        ErpB1Docs.resolve(companyId, sd);
        long sdId = idOfJson(ErpB1Docs.saveJson(companyId, 0L, sd, 0L));
        ErpB1DocPost.post(companyId, 1L, "APINV", sdId);
        eq("expense_debit", ErpGl.mappedNet(companyId, "EXPENSE", asOf), 2000L);
        eq("ap_control_after_service_invoice", 0L - ErpGl.mappedNet(companyId, AccountMapKey.AP, asOf), 47320L);
        ErpB1DocPost.reverse(companyId, 1L, "APINV", sdId);
        eq("ap_control_after_service_reverse", 0L - ErpGl.mappedNet(companyId, AccountMapKey.AP, asOf), 45200L);
        eq("expense_after_service_reverse", ErpGl.mappedNet(companyId, "EXPENSE", asOf), 0L);
    }

    static String firstLineId(long docId) {
        return n(countWhere("SELECT id FROM mk_doc_lines WHERE doc_id=" + n(docId) + " ORDER BY line_no LIMIT 1"));
    }

    static void salesScenario() {
        /* credit limit blocks */
        ErpB1Master.save(companyId, ErpB1Master.entity("partners"), custId, js("{\"creditLimitCents\":1000}"));
        String body = "{\"partnerId\":" + n(custId) + ",\"warehouseId\":" + n(whId) + ",\"lines\":[{\"itemId\":" + n(goodsId)
                + ",\"quantity\":5,\"unitPriceCents\":10000,\"discountBps\":0,\"taxBps\":1300}]}";
        B1Doc d = ErpB1Docs.parseBody(companyId, "SQ", js(body));
        ErpB1Docs.resolve(companyId, d);
        long sqId = idOfJson(ErpB1Docs.saveJson(companyId, 0L, d, 0L));
        ErpB1Docs.submit(companyId, "SQ", sqId);
        try {
            ErpB1DocCopy.copy(companyId, 0L, "SO", "SQ", sqId, null);
            bad("credit_limit_blocks_order", "no error");
        } catch (Exception ex) {
            has("credit_limit_blocks_order", ErpB1Util.exMsg(ex, ""), "CREDIT_LIMIT_EXCEEDED");
        }
        ErpB1Master.save(companyId, ErpB1Master.entity("partners"), custId, js("{\"creditLimitCents\":10000000}"));
        String cp = ErpB1DocCopy.copy(companyId, 0L, "SO", "SQ", sqId, null);
        long soId = tid(cp);
        ErpSales.submit(companyId, soId);
        ErpSales.approve(companyId, soId);
        ArrayList<Object> sol = ErpSales.loadSoLines(companyId, soId);
        OrderLineDto sl = (OrderLineDto) sol.get(0);
        PostDeliveryRequest dr = new PostDeliveryRequest();
        dr.salesOrderId = soId;
        dr.warehouseId = whId;
        dr.idempotencyKey = "b1-dl-" + n(soId);
        dr.lines = new ArrayList<Object>();
        PostLineRequest pl = new PostLineRequest();
        pl.orderLineId = sl.id;
        pl.quantity = 5;
        dr.lines.add(pl);
        PostResultDto dl = ErpPosting.postDelivery(companyId, dr);
        if (dl == null || dl.ok == 0) {
            bad("dl_post", dl == null ? "null" : dl.message);
            return;
        }
        ErpDb.reconnect();
        long dlId = ErpB1Docs.idOf(dl.documentJson);
        eq("cogs_after_dl", ErpGl.mappedNet(companyId, AccountMapKey.COGS, asOf), 20000L);
        eq("ar_provisional_exists", countWhere("SELECT COUNT(*) FROM ar_invoices WHERE company_id=" + n(companyId) + " AND source_delivery_id=" + n(dlId) + " AND is_provisional=1"), 1L);
        /* invoice from the delivery */
        String inv = ErpB1DocCopy.copy(companyId, 0L, "ARINV", "DL", dlId, null);
        long invId = idOfJson(inv);
        has("arinv_total", inv, "\"totalCents\":56500");
        ErpB1DocPost.post(companyId, 1L, "ARINV", invId);
        eq("revenue_after_invoice", 0L - ErpGl.mappedNet(companyId, AccountMapKey.REVENUE, asOf), 50000L);
        eq("output_tax_after_invoice", 0L - ErpGl.mappedNet(companyId, "OUTPUT_TAX", asOf), 6500L);
        eq("ar_control_after_invoice", ErpGl.mappedNet(companyId, AccountMapKey.AR, asOf), 56500L);
        eq("ar_subledger_converted", countWhere("SELECT COUNT(*) FROM ar_invoices WHERE company_id=" + n(companyId) + " AND source_delivery_id=" + n(dlId) + " AND is_provisional=0 AND total_amount=56500 AND status='POSTED'"), 1L);
        /* credit memo 1 unit */
        String cmBody = "{\"fromType\":\"ARINV\",\"fromId\":" + n(invId) + ",\"lines\":[{\"baseLineId\":" + firstLineId(invId) + ",\"quantity\":1}]}";
        String cm = ErpB1DocCopy.copy(companyId, 0L, "ARCM", "ARINV", invId, js(cmBody));
        long cmId = idOfJson(cm);
        has("arcm_total", cm, "\"totalCents\":11300");
        ErpB1DocPost.post(companyId, 1L, "ARCM", cmId);
        eq("ar_control_after_cm", ErpGl.mappedNet(companyId, AccountMapKey.AR, asOf), 45200L);
        eq("returns_debit", ErpGl.mappedNet(companyId, "SALES_RETURNS", asOf), 10000L);
        eq("ar_subledger_after_cm", countWhere("SELECT open_amount FROM ar_invoices WHERE company_id=" + n(companyId) + " AND source_delivery_id=" + n(dlId)), 45200L);
        try {
            ErpB1DocPost.reverse(companyId, 1L, "ARINV", invId);
            bad("invoice_with_cm_cannot_reverse", "no error");
        } catch (Exception ex2) {
            has("invoice_with_cm_cannot_reverse", ErpB1Util.exMsg(ex2, ""), "HAS_CREDIT_MEMO");
        }
        ErpB1DocPost.reverse(companyId, 1L, "ARCM", cmId);
        eq("ar_control_after_cm_reverse", ErpGl.mappedNet(companyId, AccountMapKey.AR, asOf), 56500L);
        ErpB1DocPost.reverse(companyId, 1L, "ARINV", invId);
        eq("ar_control_after_invoice_reverse", ErpGl.mappedNet(companyId, AccountMapKey.AR, asOf), 0L);
        eq("provisional_restored", countWhere("SELECT COUNT(*) FROM ar_invoices WHERE company_id=" + n(companyId) + " AND source_delivery_id=" + n(dlId) + " AND is_provisional=1 AND status='PROVISIONAL'"), 1L);
        /* FX service invoice in USD: 100.00 USD @ 7.1 => 710.00 CNY, tax 6% */
        String usd = "{\"partnerId\":" + n(custId) + ",\"currency\":\"USD\",\"lines\":[{\"itemId\":" + n(svcId) + ",\"quantity\":2,\"unitPriceCents\":5000,\"discountBps\":0,\"taxBps\":600}]}";
        B1Doc ud = ErpB1Docs.parseBody(companyId, "ARINV", js(usd));
        ErpB1Docs.resolve(companyId, ud);
        eq("usd_total_foreign", ud.totalCents, 10600L);
        eq("usd_total_local", ud.totalLocalCents, 75260L);
        long uId = idOfJson(ErpB1Docs.saveJson(companyId, 0L, ud, 0L));
        ErpB1DocPost.post(companyId, 1L, "ARINV", uId);
        eq("ar_control_usd_invoice", ErpGl.mappedNet(companyId, AccountMapKey.AR, asOf), 75260L);
        /* no-rate currency refused */
        try {
            B1Doc ed = ErpB1Docs.parseBody(companyId, "ARINV", js("{\"partnerId\":" + n(custId) + ",\"currency\":\"EUR\",\"lines\":[{\"itemId\":" + n(svcId) + ",\"quantity\":1,\"unitPriceCents\":100}]}"));
            ErpB1Docs.resolve(companyId, ed);
            bad("missing_rate_rejected", "no error");
        } catch (Exception ex3) {
            has("missing_rate_rejected", ErpB1Util.exMsg(ex3, ""), "NO_EXCHANGE_RATE");
        }
        /* invoice for a stock item without a delivery is refused */
        B1Doc sd = ErpB1Docs.parseBody(companyId, "ARINV", js("{\"partnerId\":" + n(custId) + ",\"lines\":[{\"itemId\":" + n(goodsId) + ",\"quantity\":1,\"unitPriceCents\":100}]}"));
        ErpB1Docs.resolve(companyId, sd);
        long sdId = idOfJson(ErpB1Docs.saveJson(companyId, 0L, sd, 0L));
        try {
            ErpB1DocPost.post(companyId, 1L, "ARINV", sdId);
            bad("arinv_stock_without_dl_rejected", "no error");
        } catch (Exception ex4) {
            has("arinv_stock_without_dl_rejected", ErpB1Util.exMsg(ex4, ""), "INVENTORY_REQUIRES_DELIVERY");
        }
    }

    static void ledgerScenario() {
        long tb = ErpNum.asLong(ErpDb.queryOne("SELECT COALESCE(SUM(l.debit_amount),0)-COALESCE(SUM(l.credit_amount),0) FROM journal_entry_lines l JOIN journal_entries e ON e.id=l.journal_entry_id WHERE l.company_id=" + n(companyId) + " AND e.status='POSTED'", new ArrayList<Object>(), new LongMapper()));
        eq("trial_balance_balanced", tb, 0L);
        GlReconCheckDto ar = ErpGl.checkArControl(companyId, asOf);
        eq("ar_control_vs_partner_lines", ar.ok, 1L);
        GlReconCheckDto ap = ErpGl.checkApControl(companyId, asOf);
        eq("ap_control_vs_partner_lines", ap.ok, 1L);
        GlReconCheckDto tr = ErpGl.checkTrial(companyId, asOf);
        eq("gl_trial_check", tr.ok, 1L);
    }
}
