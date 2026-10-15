/**
 * @author 张晓达
 */
class ErpJson {
    static String hex4(int n) {
        String h = "0123456789abcdef";
        int a = (n >> 12) & 15;
        int b = (n >> 8) & 15;
        int c = (n >> 4) & 15;
        int d = n & 15;
        return h.substring(a, a + 1) + h.substring(b, b + 1) + h.substring(c, c + 1) + h.substring(d, d + 1);
    }

    static String esc(String s) {
        if (s == null) {
            return "";
        }
        String out = "";
        int i = 0;
        while (i < s.length()) {
            int c = s.charAt(i);
            if (c == 34) {
                out = out + "\\\"";
            } else {
                if (c == 92) {
                    out = out + "\\\\";
                } else {
                    if (c == 10) {
                        out = out + "\\n";
                    } else {
                        if (c == 13) {
                            out = out + "\\r";
                        } else {
                            if (c == 9) {
                                out = out + "\\t";
                            } else {
                                if (c >= 32 && c <= 126) {
                                    out = out + WebUtil.byteChar(c);
                                } else {
                                    /* JSON \\uXXXX — WebUtil.byteChar maps non-ASCII to '?' */
                                    out = out + "\\u" + hex4(c);
                                }
                            }
                        }
                    }
                }
            }
            i = i + 1;
        }
        return out;
    }

    static String str(String s) {
        if (s == null) {
            return "null";
        }
        return "\"" + esc(s) + "\"";
    }

    static String num(long v) {
        return CryptoParse.longToString(v);
    }

    static String user(UserDto u) {
        if (u == null) {
            return "null";
        }
        return "{\"id\":" + num(u.id)
                + ",\"companyId\":" + num(u.companyId)
                + ",\"email\":" + str(u.email)
                + ",\"displayName\":" + str(u.displayName)
                + ",\"role\":" + str(u.role)
                + "}";
    }

    static String partner(BusinessPartnerDto p) {
        return "{\"id\":" + num(p.id)
                + ",\"code\":" + str(p.code)
                + ",\"name\":" + str(p.name)
                + ",\"kind\":" + str(p.kind)
                + "}";
    }

    static String item(ItemDto it) {
        return "{\"id\":" + num(it.id)
                + ",\"sku\":" + str(it.sku)
                + ",\"name\":" + str(it.name)
                + ",\"unitPriceCents\":" + num(it.unitPriceCents)
                + "}";
    }

    static String warehouse(WarehouseDto w) {
        return "{\"id\":" + num(w.id)
                + ",\"code\":" + str(w.code)
                + ",\"name\":" + str(w.name)
                + "}";
    }

    static String balance(InventoryBalanceDto b) {
        long avg = 0L;
        if (b.quantityOnHand > 0) {
            avg = b.valueAmount / (0L + b.quantityOnHand);
        }
        String costState = b.costState;
        if (costState == null || costState.length() == 0) {
            costState = "OK";
        }
        String whName = b.warehouseName;
        if (whName == null) {
            whName = "";
        }
        return "{\"warehouseId\":" + num(b.warehouseId)
                + ",\"itemId\":" + num(b.itemId)
                + ",\"quantityOnHand\":" + String.valueOf(b.quantityOnHand)
                + ",\"quantityReserved\":" + String.valueOf(b.quantityReserved)
                + ",\"quantityAvailable\":" + String.valueOf(b.quantityAvailable)
                + ",\"reservedSupported\":" + String.valueOf(b.reservedSupported)
                + ",\"warehouseCode\":" + str(b.warehouseCode)
                + ",\"warehouseName\":" + str(whName)
                + ",\"itemSku\":" + str(b.itemSku)
                + ",\"itemName\":" + str(b.itemName)
                + ",\"value\":" + num(b.valueAmount)
                + ",\"valueAmount\":" + num(b.valueAmount)
                + ",\"avgUnitCost\":" + num(avg)
                + ",\"minQty\":" + String.valueOf(b.minQty)
                + ",\"costState\":" + str(costState)
                + "}";
    }

    static String postResult(PostResultDto r) {
        String doc = "null";
        if (r.documentJson != null && r.documentJson.length() > 0) {
            doc = r.documentJson;
        }
        return "{\"ok\":" + String.valueOf(r.ok)
                + ",\"code\":" + str(r.code)
                + ",\"message\":" + str(r.message)
                + ",\"document\":" + doc
                + "}";
    }

    static String partnerList(ArrayList<Object> list) {
        String out = "[";
        int i = 0;
        while (i < list.size()) {
            if (i > 0) {
                out = out + ",";
            }
            out = out + partner((BusinessPartnerDto) list.get(i));
            i = i + 1;
        }
        return out + "]";
    }

    static String itemList(ArrayList<Object> list) {
        String out = "[";
        int i = 0;
        while (i < list.size()) {
            if (i > 0) {
                out = out + ",";
            }
            out = out + item((ItemDto) list.get(i));
            i = i + 1;
        }
        return out + "]";
    }

    static String warehouseList(ArrayList<Object> list) {
        String out = "[";
        int i = 0;
        while (i < list.size()) {
            if (i > 0) {
                out = out + ",";
            }
            out = out + warehouse((WarehouseDto) list.get(i));
            i = i + 1;
        }
        return out + "]";
    }

    static String balanceList(ArrayList<Object> list) {
        String out = "[";
        int i = 0;
        while (i < list.size()) {
            if (i > 0) {
                out = out + ",";
            }
            out = out + balance((InventoryBalanceDto) list.get(i));
            i = i + 1;
        }
        return out + "]";
    }
}
