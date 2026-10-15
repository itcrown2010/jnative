/**
 * Integer minor-unit (cents) document line math — matches JnDocumentCalc (exclusive tax, half-up).
 * @author 张晓达
 */
class ErpDocMoney {
    static long divHalfUp(long num, long den) {
        if (den <= 0L) {
            return 0L;
        }
        return (num + (den / 2L)) / den;
    }

    static long lineAmountCents(int quantity, long unitPriceCents, int discountBps, int taxBps) {
        if (quantity < 0) {
            quantity = 0;
        }
        if (unitPriceCents < 0L) {
            unitPriceCents = 0L;
        }
        if (discountBps < 0) {
            discountBps = 0;
        }
        if (discountBps > 10000) {
            discountBps = 10000;
        }
        if (taxBps < 0) {
            taxBps = 0;
        }
        long gross = (long) quantity * unitPriceCents;
        long discount = divHalfUp(gross * (long) discountBps, 10000L);
        long net = gross - discount;
        if (net < 0L) {
            net = 0L;
        }
        long tax = divHalfUp(net * (long) taxBps, 10000L);
        return net + tax;
    }

    static long netCents(int quantity, long unitPriceCents, int discountBps) {
        if (quantity < 0) {
            quantity = 0;
        }
        if (unitPriceCents < 0L) {
            unitPriceCents = 0L;
        }
        if (discountBps < 0) {
            discountBps = 0;
        }
        if (discountBps > 10000) {
            discountBps = 10000;
        }
        long gross = (long) quantity * unitPriceCents;
        long discount = divHalfUp(gross * (long) discountBps, 10000L);
        long net = gross - discount;
        if (net < 0L) {
            return 0L;
        }
        return net;
    }

    static long taxCents(int quantity, long unitPriceCents, int discountBps, int taxBps) {
        long net = netCents(quantity, unitPriceCents, discountBps);
        if (taxBps < 0) {
            taxBps = 0;
        }
        return divHalfUp(net * (long) taxBps, 10000L);
    }

    /** Parse epoch-millis, numeric string, or YYYY-MM-DD (UTC noon).
     * Prefer string form first: JsonObject.getLong("2026-10-15") wrongly yields 2026. */
    static long parseBizDate(JsonObject body, String primaryKey, String altKey) {
        String s = body.getString(primaryKey, "");
        if ((s == null || s.length() == 0) && altKey != null && altKey.length() > 0) {
            s = body.getString(altKey, "");
        }
        if (s != null && s.length() > 0) {
            /* digits only → millis or seconds (ASCII '0'..'9' = 48..57) */
            int i = 0;
            int digits = 1;
            while (i < s.length()) {
                int c = s.charAt(i);
                if (c < 48 || c > 57) {
                    digits = 0;
                    break;
                }
                i = i + 1;
            }
            if (digits != 0) {
                long n = ErpNum.parseLong(s);
                if (n > 0L && n < 100000000000L) {
                    return n * 1000L;
                }
                return n;
            }
            /* YYYY-MM-DD (ASCII '-' = 45) */
            if (s.length() >= 10 && s.charAt(4) == 45 && s.charAt(7) == 45) {
                int y = ErpNum.parseInt(s.substring(0, 4));
                int m = ErpNum.parseInt(s.substring(5, 7));
                int d = ErpNum.parseInt(s.substring(8, 10));
                if (y >= 1970 && m >= 1 && m <= 12 && d >= 1 && d <= 31) {
                    long day = epochDay(y, m, d);
                    return day * 86400000L + 43200000L;
                }
            }
        }
        long v = 0L;
        JsonValue jv = body.get(primaryKey);
        if (jv != null && jv.kind == JsonValue.NUMBER_KIND) {
            v = body.getLong(primaryKey, 0L);
        }
        if (v <= 0L && altKey != null && altKey.length() > 0) {
            JsonValue av = body.get(altKey);
            if (av != null && av.kind == JsonValue.NUMBER_KIND) {
                v = body.getLong(altKey, 0L);
            }
        }
        /* Reject year-only leftovers (e.g. misparsed "2026-10-15" → 2026). */
        if (v > 0L && v < 100000L) {
            throw new TransactionException("无效业务日期（需要 YYYY-MM-DD 或毫秒时间戳）");
        }
        return v;
    }

    static long epochDay(int y, int m, int d) {
        /* Howard Hinnant civil_from_days inverse (public domain algorithm) */
        long y0 = (long) y;
        long m0 = (long) m;
        long d0 = (long) d;
        y0 = y0 - (m0 <= 2L ? 1L : 0L);
        long era = (y0 >= 0L ? y0 : y0 - 399L) / 400L;
        long yoe = y0 - era * 400L;
        long doy = (153L * (m0 + (m0 > 2L ? -3L : 9L)) + 2L) / 5L + d0 - 1L;
        long doe = yoe * 365L + yoe / 4L - yoe / 100L + doy;
        return era * 146097L + doe - 719468L;
    }

    static int companyDefaultTaxBps(long companyId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        try {
            Object v = ErpDb.queryOne("SELECT default_tax_bps FROM companies WHERE id=?", p, new IntMapper());
            if (v != null) {
                return ErpNum.parseInt((String) v);
            }
        } catch (Exception ignored) {
        }
        return 0;
    }
}
