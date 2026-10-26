#!/usr/bin/env python3
"""Generate public/js/b1/b1-i18n.js (ASCII-only, \\uXXXX escapes) from the table below.

erp-server corrupts raw UTF-8 in served .js files, so the Chinese strings are authored here and
emitted escaped. Run: python3 examples/erp/scripts/gen-b1-i18n.py
"""
import json
import os

T = {
    # --- generic
    "b1.add": ("Add", "添加"),
    "b1.addLine": ("Add line", "添加行"),
    "b1.delete": ("Delete", "删除"),
    "b1.close": ("Close", "关闭"),
    "b1.yes": ("Yes", "是"),
    "b1.no": ("No", "否"),
    "b1.page": ("Page {n}", "第 {n} 页"),
    "b1.autoNo": ("(auto)", "(自动编号)"),
    "b1.unlimited": ("unlimited", "不限"),
    "b1.saveFirst": ("Save the record first to edit this tab.", "请先保存记录后再编辑此页签。"),
    "b1.pickOne": ("Select a value first", "请先选择一项"),
    "b1.pickLines": ("Select at least one line", "请至少选择一行"),
    "b1.needPartner": ("Select a business partner", "请选择业务伙伴"),
    "b1.needLines": ("Add at least one line", "请至少添加一行"),
    "b1.noLines": ("No lines. Add a line.", "暂无行，请添加。"),
    "b1.err.duplicate": ("Duplicate key: code already exists", "编码已存在，请更换"),
    "b1.err.noRate": ("No exchange rate for this currency/date", "该币种在该日期没有汇率，请先维护汇率"),
    # --- entity titles
    "b1.ent.partners": ("Business Partner", "业务伙伴"),
    "b1.ent.items": ("Item", "物料"),
    "b1.ent.bpGroups": ("BP Groups", "业务伙伴组"),
    "b1.ent.paymentTerms": ("Payment Terms", "付款条款"),
    "b1.ent.taxCodes": ("Tax Codes", "税码"),
    "b1.ent.currencies": ("Currencies", "币种"),
    "b1.ent.exchangeRates": ("Exchange Rates", "汇率"),
    "b1.ent.priceLists": ("Price Lists", "价格表"),
    "b1.ent.itemGroups": ("Item Groups", "物料组"),
    "b1.ent.uomGroups": ("UoM Groups", "计量单位组"),
    # --- tabs
    "b1.tab.general": ("General", "常规"),
    "b1.tab.terms": ("Payment & Terms", "付款与条款"),
    "b1.tab.contacts": ("Contact Persons", "联系人"),
    "b1.tab.addresses": ("Addresses", "地址"),
    "b1.tab.credit": ("Credit & Exposure", "信用与风险敞口"),
    "b1.tab.remarks": ("Remarks", "备注"),
    "b1.tab.sales": ("Sales", "销售"),
    "b1.tab.purchasing": ("Purchasing", "采购"),
    "b1.tab.inventory": ("Inventory", "库存"),
    "b1.tab.prices": ("Prices", "价格"),
    "b1.tab.conversions": ("UoM Conversions", "单位换算"),
    # --- enumerations
    "b1.kind.customer": ("Customer", "客户"),
    "b1.kind.supplier": ("Supplier", "供应商"),
    "b1.kind.both": ("Customer & Supplier", "客户兼供应商"),
    "b1.kind.lead": ("Lead", "潜在客户"),
    "b1.st.active": ("Active", "有效"),
    "b1.st.frozen": ("Frozen", "冻结"),
    "b1.st.inactive": ("Inactive", "无效"),
    "b1.itype.item": ("Item", "物料"),
    "b1.itype.service": ("Service", "服务"),
    "b1.itype.labor": ("Labor", "人工"),
    "b1.itype.travel": ("Travel", "差旅"),
    "b1.manage.n": ("None", "不管理"),
    "b1.manage.b": ("Batch", "批次"),
    "b1.manage.s": ("Serial", "序列号"),
    "b1.val.ma": ("Moving average", "移动平均"),
    "b1.val.std": ("Standard", "标准成本"),
    "b1.val.fifo": ("FIFO", "先进先出"),
    "b1.dir.out": ("Output (sales)", "销项(销售)"),
    "b1.dir.in": ("Input (purchase)", "进项(采购)"),
    "b1.dir.both": ("Both", "进销通用"),
    "b1.addr.bill": ("Bill-to", "开票地址"),
    "b1.addr.ship": ("Ship-to", "收货地址"),
    # --- hints
    "b1.hint.currency": ("Document currency; exchange rate required for foreign currency", "单据币种；外币需要先维护汇率"),
    "b1.hint.credit0": ("0 = unlimited", "0 表示不限额"),
    "b1.hint.factor": ("Percent of the base list price (100 = same price)", "相对基础价格表的百分比(100 表示同价)"),
    # --- fields
    "b1.f.code": ("Code", "编码"),
    "b1.f.name": ("Name", "名称"),
    "b1.f.kind": ("Type", "类型"),
    "b1.f.groupId": ("Group", "分组"),
    "b1.f.status": ("Status", "状态"),
    "b1.f.taxId": ("Tax ID", "税号"),
    "b1.f.phone": ("Phone", "电话"),
    "b1.f.email": ("Email", "邮箱"),
    "b1.f.website": ("Website", "网站"),
    "b1.f.industry": ("Industry", "行业"),
    "b1.f.paymentTermsId": ("Payment Terms", "付款条款"),
    "b1.f.priceListId": ("Price List", "价格表"),
    "b1.f.currency": ("Currency", "币种"),
    "b1.f.currencyCode": ("Currency", "币种"),
    "b1.f.discountBps": ("Default Discount %", "默认折扣 %"),
    "b1.f.defaultTaxCodeId": ("Default Tax Code", "默认税码"),
    "b1.f.creditLimitCents": ("Credit Limit", "信用额度"),
    "b1.f.commitmentLimitCents": ("Commitment Limit", "承诺额度"),
    "b1.f.bankName": ("Bank", "开户行"),
    "b1.f.bankAccount": ("Bank Account", "银行账号"),
    "b1.f.remarks": ("Remarks", "备注"),
    "b1.f.sku": ("Item No.", "物料编号"),
    "b1.f.itemType": ("Item Type", "物料类型"),
    "b1.f.itemGroupId": ("Item Group", "物料组"),
    "b1.f.barcode": ("Barcode", "条码"),
    "b1.f.categoryCode": ("Category", "分类"),
    "b1.f.uom": ("Inventory UoM", "库存单位"),
    "b1.f.uomGroupId": ("UoM Group", "计量单位组"),
    "b1.f.isSales": ("Sales Item", "可销售"),
    "b1.f.unitPriceCents": ("Base Price", "基础价格"),
    "b1.f.salesUom": ("Sales UoM", "销售单位"),
    "b1.f.salesTaxCodeId": ("Sales Tax Code", "销售税码"),
    "b1.f.isPurchase": ("Purchase Item", "可采购"),
    "b1.f.purchaseUom": ("Purchase UoM", "采购单位"),
    "b1.f.purchaseTaxCodeId": ("Purchase Tax Code", "采购税码"),
    "b1.f.preferredVendorId": ("Preferred Vendor", "首选供应商"),
    "b1.f.leadTimeDays": ("Lead Time (days)", "提前期(天)"),
    "b1.f.isInventory": ("Inventory Item", "库存物料"),
    "b1.f.manageMethod": ("Manage By", "管理方式"),
    "b1.f.valuationMethod": ("Valuation Method", "计价方法"),
    "b1.f.defaultWarehouseId": ("Default Warehouse", "默认仓库"),
    "b1.f.minQty": ("Min Stock", "最低库存"),
    "b1.f.maxQty": ("Max Stock", "最高库存"),
    "b1.f.reorderQty": ("Reorder Qty", "再订货量"),
    "b1.f.weightG": ("Weight (g)", "重量(克)"),
    "b1.f.netDays": ("Net Days", "账期天数"),
    "b1.f.discountDays": ("Discount Days", "折扣天数"),
    "b1.f.rateBps": ("Rate %", "税率 %"),
    "b1.f.direction": ("Direction", "方向"),
    "b1.f.active": ("Active", "启用"),
    "b1.f.symbol": ("Symbol", "符号"),
    "b1.f.decimals": ("Decimals", "小数位"),
    "b1.f.isLocal": ("Local", "本位币"),
    "b1.f.rateDate": ("Rate Date", "汇率日期"),
    "b1.f.rateMicro": ("Rate (local per 1 foreign)", "汇率(1 外币折合本币)"),
    "b1.f.rate": ("Rate", "汇率"),
    "b1.f.baseListId": ("Base List", "基础价格表"),
    "b1.f.factorBps": ("Factor %", "系数 %"),
    "b1.f.isDefault": ("Default", "默认"),
    "b1.f.baseUom": ("Base UoM", "基本单位"),
    "b1.f.uomCode": ("UoM", "单位"),
    "b1.f.baseQty": ("Base Qty per UoM", "折合基本单位数量"),
    "b1.f.title": ("Title", "职务"),
    "b1.f.mobile": ("Mobile", "手机"),
    "b1.f.addrType": ("Address Type", "地址类型"),
    "b1.f.addrName": ("Address Name", "地址名称"),
    "b1.f.street": ("Street", "街道"),
    "b1.f.city": ("City", "城市"),
    "b1.f.state": ("State/Province", "省/州"),
    "b1.f.zip": ("Postal Code", "邮编"),
    "b1.f.country": ("Country", "国家"),
    "b1.f.itemId": ("Item", "物料"),
    "b1.f.priceCents": ("Price", "价格"),
    # --- documents
    "b1.doc.SQ": ("Sales Quotation", "销售报价单"),
    "b1.doc.ARINV": ("A/R Invoice", "应收发票"),
    "b1.doc.ARCM": ("A/R Credit Memo", "应收贷项凭证"),
    "b1.doc.PQ": ("Purchase Quotation", "采购报价单"),
    "b1.doc.APINV": ("A/P Invoice", "应付发票"),
    "b1.doc.APCM": ("A/P Credit Memo", "应付贷项凭证"),
    "b1.base.DL": ("Delivery", "销售交货"),
    "b1.base.SO": ("Sales Order", "销售订单"),
    "b1.base.GR": ("Goods Receipt", "采购收货"),
    "b1.base.PO": ("Purchase Order", "采购订单"),
    "b1.f.docNo": ("Doc No", "单据编号"),
    "b1.f.docDate": ("Posting Date", "过账日期"),
    "b1.f.dueDate": ("Due Date", "到期日"),
    "b1.f.validUntil": ("Valid Until", "有效期至"),
    "b1.f.customer": ("Customer", "客户"),
    "b1.f.supplier": ("Supplier", "供应商"),
    "b1.f.warehouseId": ("Warehouse", "仓库"),
    "b1.f.refNo": ("Reference", "参考号"),
    "b1.f.headerDiscount": ("Header Discount %", "整单折扣 %"),
    "b1.f.shipTo": ("Ship-to / Bill-to", "收货/开票地址"),
    "b1.f.notes": ("Remarks", "备注"),
    "b1.f.item": ("Item", "物料"),
    "b1.f.description": ("Description", "描述"),
    "b1.f.quantity": ("Qty", "数量"),
    "b1.f.unitPrice": ("Unit Price", "单价"),
    "b1.f.discount": ("Disc %", "折扣 %"),
    "b1.f.taxCode": ("Tax Code", "税码"),
    "b1.f.net": ("Net", "未税金额"),
    "b1.f.tax": ("Tax", "税额"),
    "b1.f.total": ("Total", "合计"),
    "b1.f.totalLocal": ("Total (local)", "合计(本币)"),
    "b1.f.subtotal": ("Subtotal", "小计"),
    "b1.f.base": ("Base", "基础单据"),
    "b1.f.used": ("Already copied", "已复制"),
    "b1.f.open": ("Open", "未清"),
    "b1.f.copyQty": ("Copy Qty", "复制数量"),
    "b1.lines": ("Lines", "行项目"),
    "b1.post": ("Post", "过账"),
    "b1.posted": ("Posted", "已过账"),
    "b1.submit": ("Submit / Release", "提交/释放"),
    "b1.submitted": ("Submitted", "已提交"),
    "b1.cancelDoc": ("Cancel document", "作废单据"),
    "b1.cancelled": ("Cancelled", "已作废"),
    "b1.reverse": ("Reverse", "冲销"),
    "b1.reversed": ("Reversed", "已冲销"),
    "b1.reverseConfirm": ("Reverse this posted document? Journal and sub-ledger effects will be reversed.", "确认冲销该已过账单据？将同时冲销分录与明细账。"),
    "b1.copyFrom": ("Copy from...", "复制自..."),
    "b1.createDraft": ("Create draft", "生成草稿"),
    "b1.copied": ("Created {type} {no}", "已生成 {type} {no}"),
    "b1.copy.toSO": ("Copy to Sales Order", "复制到销售订单"),
    "b1.copy.toPO": ("Copy to Purchase Order", "复制到采购订单"),
    "b1.copy.toARCM": ("Copy to A/R Credit Memo", "复制到应收贷项凭证"),
    "b1.copy.toAPCM": ("Copy to A/P Credit Memo", "复制到应付贷项凭证"),
    "b1.target": ("Target", "目标单据"),
    "b1.journal": ("Journal entry", "会计分录"),
    "b1.subledger": ("Sub-ledger", "明细账"),
    # --- credit
    "b1.credit.limit": ("Credit limit", "信用额度"),
    "b1.credit.balance": ("Account balance (AR/AP)", "账户余额(应收/应付)"),
    "b1.credit.orders": ("Open orders", "未清订单"),
    "b1.credit.exposure": ("Total exposure", "总风险敞口"),
    "b1.credit.blocked": ("Blocked", "已超限"),
    "b1.credit.note": ("A credit limit of 0 means unlimited. Orders and documents are blocked when exposure exceeds the limit.", "信用额度为 0 表示不限额。风险敞口超过额度时将阻止下单与开票。"),
    # --- page titles (registry)
    "page.md.exchangeRates": ("Exchange Rates", "汇率"),
    "page.md.bpGroups": ("BP Groups", "业务伙伴组"),
    "page.md.paymentTerms": ("Payment Terms", "付款条款"),
    "page.md.taxCodes": ("Tax Codes", "税码"),
    "page.md.itemGroups": ("Item Groups", "物料组"),
    "page.md.uomGroups": ("UoM Groups", "计量单位组"),
    "page.md.customers": ("Customers", "客户主数据"),
    "page.md.suppliers": ("Suppliers", "供应商主数据"),
    "page.md.items": ("Items", "物料主数据"),
    "page.md.pricelists": ("Price Lists", "价格表"),
    "page.md.currencies": ("Currencies", "币种"),
    "page.sales.quotations": ("Sales Quotations", "销售报价单"),
    "page.sales.ar": ("AR Invoices", "应收发票"),
    "page.sales.arcm": ("AR Credit Memos", "应收贷项凭证"),
    "page.pur.quotations": ("Purchase Quotations", "采购报价单"),
    "page.pur.ap": ("AP Invoices", "应付发票"),
    "page.pur.apcm": ("AP Credit Memos", "应付贷项凭证"),
}


def esc(s):
    out = []
    for ch in s:
        o = ord(ch)
        if ch == '"' or ch == "\\":
            out.append("\\" + ch)
        elif 32 <= o < 127:
            out.append(ch)
        else:
            out.append("\\u%04x" % o)
    return "".join(out)


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    out_path = os.path.normpath(os.path.join(here, "..", "public", "js", "b1", "b1-i18n.js"))
    lines = [
        "// JNative ERP One (b1 module) - GENERATED by scripts/gen-b1-i18n.py; do not edit by hand.",
        "(function (global) {",
        "  var en = {",
    ]
    keys = sorted(T)
    lines.append(",\n".join('    "%s": "%s"' % (esc(k), esc(T[k][0])) for k in keys))
    lines.append("  };")
    lines.append("  var zh = {")
    lines.append(",\n".join('    "%s": "%s"' % (esc(k), esc(T[k][1])) for k in keys))
    lines.append("  };")
    lines.append("  if (global.JnI18n && global.JnI18n.extend) {")
    lines.append('    global.JnI18n.extend("en", en);')
    lines.append('    global.JnI18n.extend("zh-CN", zh);')
    lines.append("  }")
    lines.append("})(window);")
    with open(out_path, "w", encoding="ascii") as f:
        f.write("\n".join(lines) + "\n")
    print("wrote", out_path, len(keys), "keys")


if __name__ == "__main__":
    main()
