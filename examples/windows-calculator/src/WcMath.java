/**
 * Scientific functions for the JNative Windows Calculator.
 *
 * ln / exp / sin / cos / atan are computed in 40-digit decimal arithmetic
 * (argument reduction + Taylor series), so results carry the 32 digits the
 * in-box Calculator shows. Only factorial of non-integers (Lanczos gamma) and
 * the random generator use libm doubles.
 *
 * @author 张晓达
 */
class WcMath {
    static final int DEG = 0;
    static final int RAD = 1;
    static final int GRAD = 2;

    static WcNum PI = new WcNum(1, "3141592653589793238462643383279502884197", -39);
    static WcNum E = new WcNum(1, "2718281828459045235360287471352662497757", -39);
    static WcNum LN2 = new WcNum(1, "6931471805599453094172321214581765680755", -40);
    static WcNum LN10 = new WcNum(1, "2302585092994045684017991454684364207601", -39);
    static WcNum HALF_PI = new WcNum(1, "1570796326794896619231321691639751442099", -39);
    static WcNum HALF = new WcNum(1, "5", -1);
    static WcNum EPS = new WcNum(1, "1", -46);

    static long rndState = 0L;

    static WcNum fromD(double v) {
        return WcNum.fromDouble(v);
    }

    static WcNum two() {
        return WcNum.fromLong(2L);
    }

    /** Clean a computed value for output (hide guard digits). */
    static WcNum out(WcNum v) {
        return v.roundSig(36);
    }

    /* ------------------------------------------------------------------ */
    /* Core: ln, exp, sin/cos, atan in 40-digit arithmetic                  */
    /* ------------------------------------------------------------------ */

    static WcNum lnHp(WcNum x) {
        int e10 = x.exp10();
        WcNum m = x.shiftExp(0 - e10);
        WcNum limit = new WcNum(1, "15", -1);
        int j = 0;
        while (m.cmp(limit) > 0) {
            m = m.div(two());
            j = j + 1;
        }
        WcNum z = m.sub(WcNum.ONE).div(m.add(WcNum.ONE));
        WcNum z2 = z.mul(z);
        WcNum term = z;
        WcNum sum = z;
        long n = 1L;
        int guard = 0;
        while (guard < 200 && term.sign != 0) {
            term = term.mul(z2);
            n = n + 2L;
            WcNum t = term.div(WcNum.fromLong(n));
            if (t.cmpAbs(EPS) < 0) {
                break;
            }
            sum = sum.add(t);
            guard = guard + 1;
        }
        WcNum r = sum.mul(two());
        if (j != 0) {
            r = r.add(LN2.mul(WcNum.fromLong((long) j)));
        }
        if (e10 != 0) {
            r = r.add(LN10.mul(WcNum.fromLong((long) e10)));
        }
        return r;
    }

    static WcNum expHp(WcNum x) {
        if (x.sign == 0) {
            return WcNum.ONE;
        }
        if (x.exp10() >= 6) {
            if (x.sign > 0) {
                throw WcErr.make(WcErr.OVERFLOW);
            }
            return WcNum.ZERO;
        }
        WcNum q = x.div(LN10);
        WcNum nr;
        if (q.sign > 0) {
            nr = q.add(HALF).truncate();
        } else {
            nr = q.sub(HALF).truncate();
        }
        long nl = nr.toLong();
        if (nl > 10000L) {
            throw WcErr.make(WcErr.OVERFLOW);
        }
        if (nl < -10000L) {
            return WcNum.ZERO;
        }
        WcNum r = x.sub(nr.mul(LN10));
        WcNum r16 = r.div(WcNum.fromLong(16L));
        WcNum sum = WcNum.ONE;
        WcNum term = WcNum.ONE;
        long k = 1L;
        int guard = 0;
        while (guard < 120) {
            term = term.mul(r16).div(WcNum.fromLong(k));
            if (term.cmpAbs(EPS) < 0) {
                break;
            }
            sum = sum.add(term);
            k = k + 1L;
            guard = guard + 1;
        }
        int sq = 0;
        while (sq < 4) {
            sum = sum.mul(sum);
            sq = sq + 1;
        }
        return sum.shiftExp((int) nl).checkRange();
    }

    /** Returns [sin, cos] of a radian argument. */
    static WcNum[] sinCosHp(WcNum x) {
        WcNum q = x.div(HALF_PI);
        WcNum qi;
        if (q.sign >= 0) {
            qi = q.add(HALF).truncate();
        } else {
            qi = q.sub(HALF).truncate();
        }
        WcNum r = x.sub(qi.mul(HALF_PI));
        WcNum r2 = r.mul(r);
        /* sin */
        WcNum sTerm = r;
        WcNum sSum = r;
        long n = 1L;
        int guard = 0;
        while (guard < 80 && sTerm.sign != 0) {
            sTerm = sTerm.mul(r2).div(WcNum.fromLong((n + 1L) * (n + 2L))).negate();
            n = n + 2L;
            if (sTerm.cmpAbs(EPS) < 0) {
                break;
            }
            sSum = sSum.add(sTerm);
            guard = guard + 1;
        }
        /* cos */
        WcNum cTerm = WcNum.ONE;
        WcNum cSum = WcNum.ONE;
        n = 0L;
        guard = 0;
        while (guard < 80) {
            cTerm = cTerm.mul(r2).div(WcNum.fromLong((n + 1L) * (n + 2L))).negate();
            n = n + 2L;
            if (cTerm.cmpAbs(EPS) < 0) {
                break;
            }
            cSum = cSum.add(cTerm);
            guard = guard + 1;
        }
        long qv = 0L;
        if (qi.exp10() < 15) {
            qv = qi.rem(WcNum.fromLong(4L)).toLong();
        }
        if (qv < 0L) {
            qv = qv + 4L;
        }
        WcNum[] res = new WcNum[2];
        if (qv == 0L) {
            res[0] = sSum;
            res[1] = cSum;
        } else {
            if (qv == 1L) {
                res[0] = cSum;
                res[1] = sSum.negate();
            } else {
                if (qv == 2L) {
                    res[0] = sSum.negate();
                    res[1] = cSum.negate();
                } else {
                    res[0] = cSum.negate();
                    res[1] = sSum;
                }
            }
        }
        return res;
    }

    static WcNum atanHp(WcNum x) {
        if (x.sign == 0) {
            return WcNum.ZERO;
        }
        if (x.sign < 0) {
            return atanHp(x.negate()).negate();
        }
        if (x.cmp(WcNum.ONE) > 0) {
            return HALF_PI.sub(atanHp(WcNum.ONE.div(x)));
        }
        WcNum a = x;
        int halvings = 3;
        int h = 0;
        while (h < halvings) {
            a = a.div(WcNum.ONE.add(WcNum.ONE.add(a.mul(a)).sqrtNum()));
            h = h + 1;
        }
        WcNum a2 = a.mul(a);
        WcNum term = a;
        WcNum sum = a;
        long n = 1L;
        int guard = 0;
        while (guard < 120) {
            term = term.mul(a2).negate();
            n = n + 2L;
            WcNum t = term.div(WcNum.fromLong(n));
            if (t.cmpAbs(EPS) < 0) {
                break;
            }
            sum = sum.add(t);
            guard = guard + 1;
        }
        return sum.mul(WcNum.fromLong(8L));
    }

    /* ------------------------------------------------------------------ */
    /* Trigonometry                                                        */
    /* ------------------------------------------------------------------ */

    /** Angle in the active unit -> radians, with exact modular reduction. */
    static WcNum radians(WcNum x, int mode) {
        if (mode == RAD) {
            return x;
        }
        WcNum period = WcNum.fromLong(360L);
        WcNum half = WcNum.fromLong(180L);
        if (mode == GRAD) {
            period = WcNum.fromLong(400L);
            half = WcNum.fromLong(200L);
        }
        WcNum a = x.rem(period);
        return a.mul(PI).div(half);
    }

    /** Radians -> active angle unit. */
    static WcNum fromRadians(WcNum r, int mode) {
        if (mode == RAD) {
            return out(r);
        }
        if (mode == GRAD) {
            return out(r.mul(WcNum.fromLong(200L)).div(PI));
        }
        return out(r.mul(WcNum.fromLong(180L)).div(PI));
    }

    static WcNum cleanTrig(WcNum v, WcNum rad) {
        WcNum r = out(v);
        if (r.sign != 0 && r.exp10() < -32 && rad.cmpAbs(new WcNum(1, "1", -3)) > 0) {
            return WcNum.ZERO;
        }
        return r;
    }

    static WcNum sin(WcNum x, int mode) {
        WcNum r = radians(x, mode);
        WcNum[] sc = sinCosHp(r);
        return cleanTrig(sc[0], r);
    }

    static WcNum cos(WcNum x, int mode) {
        WcNum r = radians(x, mode);
        WcNum[] sc = sinCosHp(r);
        return cleanTrig(sc[1], r);
    }

    static WcNum tan(WcNum x, int mode) {
        WcNum r = radians(x, mode);
        WcNum[] sc = sinCosHp(r);
        WcNum c = cleanTrig(sc[1], r);
        if (c.sign == 0) {
            throw WcErr.make(WcErr.DIV_ZERO);
        }
        WcNum s = cleanTrig(sc[0], r);
        return out(s.div(c));
    }

    static WcNum sec(WcNum x, int mode) {
        return out(WcNum.ONE.div(cos(x, mode)));
    }

    static WcNum csc(WcNum x, int mode) {
        return out(WcNum.ONE.div(sin(x, mode)));
    }

    static WcNum cot(WcNum x, int mode) {
        WcNum t = tan(x, mode);
        if (t.sign == 0) {
            throw WcErr.make(WcErr.DIV_ZERO);
        }
        return out(WcNum.ONE.div(t));
    }

    static void checkUnit(WcNum x) {
        if (x.cmpAbs(WcNum.ONE) > 0) {
            throw WcErr.make(WcErr.INVALID);
        }
    }

    static WcNum asinRad(WcNum x) {
        checkUnit(x);
        if (x.cmpAbs(WcNum.ONE) == 0) {
            if (x.sign > 0) {
                return HALF_PI;
            }
            return HALF_PI.negate();
        }
        WcNum den = WcNum.ONE.sub(x.mul(x)).sqrtNum();
        return atanHp(x.div(den));
    }

    static WcNum asin(WcNum x, int mode) {
        return fromRadians(asinRad(x), mode);
    }

    static WcNum acos(WcNum x, int mode) {
        checkUnit(x);
        return fromRadians(HALF_PI.sub(asinRad(x)), mode);
    }

    static WcNum atan(WcNum x, int mode) {
        return fromRadians(atanHp(x), mode);
    }

    static WcNum asec(WcNum x, int mode) {
        if (x.sign == 0) {
            throw WcErr.make(WcErr.INVALID);
        }
        return acos(WcNum.ONE.div(x), mode);
    }

    static WcNum acsc(WcNum x, int mode) {
        if (x.sign == 0) {
            throw WcErr.make(WcErr.INVALID);
        }
        return asin(WcNum.ONE.div(x), mode);
    }

    static WcNum acot(WcNum x, int mode) {
        if (x.sign == 0) {
            return fromRadians(HALF_PI, mode);
        }
        return atan(WcNum.ONE.div(x), mode);
    }

    /* ------------------------------------------------------------------ */
    /* Hyperbolic                                                          */
    /* ------------------------------------------------------------------ */

    static WcNum sinh(WcNum x) {
        WcNum a = expHp(x);
        WcNum b = WcNum.ONE.div(a);
        return out(a.sub(b).div(two()));
    }

    static WcNum cosh(WcNum x) {
        WcNum a = expHp(x);
        WcNum b = WcNum.ONE.div(a);
        return out(a.add(b).div(two()));
    }

    static WcNum tanh(WcNum x) {
        if (x.exp10() >= 2 && x.cmpAbs(WcNum.fromLong(120L)) > 0) {
            if (x.sign > 0) {
                return WcNum.ONE;
            }
            return WcNum.ONE.negate();
        }
        WcNum a = expHp(x);
        WcNum b = WcNum.ONE.div(a);
        return out(a.sub(b).div(a.add(b)));
    }

    static WcNum sech(WcNum x) {
        return out(WcNum.ONE.div(cosh(x)));
    }

    static WcNum csch(WcNum x) {
        return out(WcNum.ONE.div(sinh(x)));
    }

    static WcNum coth(WcNum x) {
        return out(WcNum.ONE.div(tanh(x)));
    }

    static WcNum asinh(WcNum x) {
        if (x.sign == 0) {
            return WcNum.ZERO;
        }
        WcNum a = x.abs();
        WcNum r = lnHp(a.add(a.mul(a).add(WcNum.ONE).sqrtNum()));
        if (x.sign < 0) {
            r = r.negate();
        }
        return out(r);
    }

    static WcNum acosh(WcNum x) {
        if (x.cmp(WcNum.ONE) < 0) {
            throw WcErr.make(WcErr.INVALID);
        }
        return out(lnHp(x.add(x.mul(x).sub(WcNum.ONE).sqrtNum())));
    }

    static WcNum atanh(WcNum x) {
        if (x.cmpAbs(WcNum.ONE) >= 0) {
            throw WcErr.make(WcErr.INVALID);
        }
        if (x.sign == 0) {
            return WcNum.ZERO;
        }
        return out(lnHp(WcNum.ONE.add(x).div(WcNum.ONE.sub(x))).mul(HALF));
    }

    static WcNum asech(WcNum x) {
        if (x.sign <= 0) {
            throw WcErr.make(WcErr.INVALID);
        }
        return acosh(WcNum.ONE.div(x));
    }

    static WcNum acsch(WcNum x) {
        if (x.sign == 0) {
            throw WcErr.make(WcErr.INVALID);
        }
        return asinh(WcNum.ONE.div(x));
    }

    static WcNum acoth(WcNum x) {
        if (x.sign == 0) {
            throw WcErr.make(WcErr.INVALID);
        }
        return atanh(WcNum.ONE.div(x));
    }

    /* ------------------------------------------------------------------ */
    /* Logs, exponentials, powers, roots                                   */
    /* ------------------------------------------------------------------ */

    static WcNum ln(WcNum x) {
        if (x.sign <= 0) {
            throw WcErr.make(WcErr.INVALID);
        }
        if (x.equalsNum(WcNum.ONE) != 0) {
            return WcNum.ZERO;
        }
        return out(lnHp(x));
    }

    static WcNum log10(WcNum x) {
        if (x.sign <= 0) {
            throw WcErr.make(WcErr.INVALID);
        }
        if (x.digits.equals("1")) {
            return WcNum.fromLong((long) x.exp);
        }
        return out(lnHp(x).div(LN10));
    }

    /** log base b of x. */
    static WcNum logBase(WcNum x, WcNum b) {
        if (x.sign <= 0 || b.sign <= 0) {
            throw WcErr.make(WcErr.INVALID);
        }
        if (b.equalsNum(WcNum.ONE) != 0) {
            throw WcErr.make(WcErr.INVALID);
        }
        if (x.equalsNum(WcNum.ONE) != 0) {
            return WcNum.ZERO;
        }
        return out(lnHp(x).div(lnHp(b)));
    }

    static WcNum expE(WcNum x) {
        if (x.sign == 0) {
            return WcNum.ONE;
        }
        return out(expHp(x));
    }

    static WcNum pow10(WcNum x) {
        if (x.isInteger() != 0 && x.exp10() < 5) {
            return WcNum.pow10((int) x.toLong()).checkRange();
        }
        return pow(WcNum.fromLong(10L), x);
    }

    static WcNum pow2(WcNum x) {
        return pow(WcNum.fromLong(2L), x);
    }

    /** x^y */
    static WcNum pow(WcNum x, WcNum y) {
        if (y.sign == 0) {
            if (x.sign == 0) {
                throw WcErr.make(WcErr.UNDEFINED);
            }
            return WcNum.ONE;
        }
        if (x.sign == 0) {
            if (y.sign < 0) {
                throw WcErr.make(WcErr.DIV_ZERO);
            }
            return WcNum.ZERO;
        }
        if (x.equalsNum(WcNum.ONE) != 0) {
            return WcNum.ONE;
        }
        if (y.isInteger() != 0 && y.exp10() < 6) {
            long n = y.toLong();
            long an = n;
            if (an < 0L) {
                an = 0L - an;
            }
            /* estimate decimal magnitude to bail out early */
            WcNum lg = lnHp(x.abs()).div(LN10);
            double est = lg.toDouble() * (double) an;
            if (est > 10010.0) {
                if (n > 0L) {
                    throw WcErr.make(WcErr.OVERFLOW);
                }
                return WcNum.ZERO;
            }
            if (est < -10010.0) {
                if (n > 0L) {
                    return WcNum.ZERO;
                }
                throw WcErr.make(WcErr.OVERFLOW);
            }
            return out(x.powInt(n));
        }
        if (x.sign < 0) {
            throw WcErr.make(WcErr.INVALID);
        }
        if (y.equalsNum(HALF) != 0) {
            return sqrt(x);
        }
        return out(expHp(lnHp(x).mul(y)));
    }

    static WcNum sqrt(WcNum x) {
        if (x.sign < 0) {
            throw WcErr.make(WcErr.INVALID);
        }
        return out(x.sqrtNum());
    }

    /** n-th root with exact snapping for perfect powers. */
    static WcNum rootInt(WcNum x, long n) {
        WcNum a = x.abs();
        WcNum r = out(expHp(lnHp(a).div(WcNum.fromLong(n))));
        WcNum snap = r.roundSig(30);
        if (snap.powInt(n).roundSig(34).equalsNum(a.roundSig(34)) != 0) {
            r = snap;
        }
        if (x.sign < 0) {
            return r.negate();
        }
        return r;
    }

    static WcNum cbrt(WcNum x) {
        if (x.sign == 0) {
            return WcNum.ZERO;
        }
        return rootInt(x, 3L);
    }

    /** x^(1/y) */
    static WcNum yroot(WcNum x, WcNum y) {
        if (y.sign == 0) {
            throw WcErr.make(WcErr.DIV_ZERO);
        }
        if (y.equalsNum(WcNum.fromLong(2L)) != 0) {
            return sqrt(x);
        }
        if (x.sign == 0) {
            if (y.sign < 0) {
                throw WcErr.make(WcErr.DIV_ZERO);
            }
            return WcNum.ZERO;
        }
        if (y.isInteger() != 0 && y.exp10() < 6) {
            long n = y.toLong();
            if (n > 0L) {
                if (x.sign < 0) {
                    if ((n % 2L) == 0L) {
                        throw WcErr.make(WcErr.INVALID);
                    }
                }
                return rootInt(x, n);
            }
        }
        if (x.sign < 0) {
            throw WcErr.make(WcErr.INVALID);
        }
        return pow(x, WcNum.ONE.div(y));
    }

    /** Gamma(x + 1) = x! for real x >= 0 (Lanczos, g = 7; ~15 digits). */
    static WcNum gammaPlus1(double x) {
        double g = 7.0;
        double[] c = new double[9];
        c[0] = 0.99999999999980993;
        c[1] = 676.5203681218851;
        c[2] = -1259.1392167224028;
        c[3] = 771.32342877765313;
        c[4] = -176.61502916214059;
        c[5] = 12.507343278686905;
        c[6] = -0.13857109526572012;
        c[7] = 9.9843695780195716e-6;
        c[8] = 1.5056327351493116e-7;
        double xx = c[0];
        int i = 1;
        while (i < 9) {
            xx = xx + c[i] / (x + (double) i);
            i = i + 1;
        }
        double t = x + g + 0.5;
        double lg = (x + 0.5) * Math.log(t) - t;
        double r = Math.sqrt(2.0 * 3.141592653589793) * Math.exp(lg) * xx;
        return fromD(r);
    }

    static WcNum factorial(WcNum x) {
        if (x.sign < 0) {
            throw WcErr.make(WcErr.INVALID);
        }
        if (x.isInteger() == 0) {
            if (x.cmp(WcNum.fromLong(168L)) > 0) {
                throw WcErr.make(WcErr.OVERFLOW);
            }
            return gammaPlus1(x.toDouble());
        }
        if (x.cmp(WcNum.fromLong(3248L)) > 0) {
            throw WcErr.make(WcErr.OVERFLOW);
        }
        long n = x.toLong();
        WcNum r = WcNum.ONE;
        long i = 2L;
        while (i <= n) {
            r = r.mul(WcNum.fromLong(i));
            i = i + 1L;
        }
        return r;
    }

    static double nextRandom() {
        if (rndState == 0L) {
            rndState = 88172645463325252L + Instant.now().toEpochMilli();
        }
        rndState = rndState * 6364136223846793005L + 1442695040888963407L;
        long v = (rndState >>> 11) & 9007199254740991L;
        return (double) v / 9007199254740992.0;
    }

    static WcNum random() {
        double d = nextRandom();
        if (d <= 0.0) {
            d = 0.5;
        }
        return fromD(d).roundSig(15);
    }

    /** Decimal degrees -> d.mmss */
    static WcNum toDms(WcNum x) {
        WcNum sg = WcNum.ONE;
        WcNum a = x;
        if (x.sign < 0) {
            sg = WcNum.ONE.negate();
            a = x.negate();
        }
        WcNum d = a.truncate();
        WcNum frac = a.sub(d);
        WcNum minsAll = frac.mul(WcNum.fromLong(60L));
        WcNum m = minsAll.truncate();
        WcNum s = minsAll.sub(m).mul(WcNum.fromLong(60L));
        WcNum r = d.add(m.div(WcNum.fromLong(100L))).add(s.div(WcNum.fromLong(10000L)));
        return out(r.mul(sg));
    }

    /** d.mmss -> decimal degrees */
    static WcNum fromDms(WcNum x) {
        WcNum sg = WcNum.ONE;
        WcNum a = x;
        if (x.sign < 0) {
            sg = WcNum.ONE.negate();
            a = x.negate();
        }
        WcNum d = a.truncate();
        WcNum frac = a.sub(d).mul(WcNum.fromLong(100L));
        WcNum m = frac.truncate();
        WcNum s = frac.sub(m).mul(WcNum.fromLong(100L));
        WcNum r = d.add(m.div(WcNum.fromLong(60L))).add(s.div(WcNum.fromLong(3600L)));
        return out(r.mul(sg));
    }
}
