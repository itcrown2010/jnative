#!/usr/bin/env python3
# Author: 张晓达
"""HTTP negative / rollback gate on an isolated SQLite DB and non-18700 port."""
from __future__ import annotations

import json
import os
import signal
import sqlite3
import subprocess
import sys
import time
import urllib.error
import urllib.request
import http.cookiejar
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
BIN = ROOT / "examples" / "erp" / ".jnative" / "build" / "erp-server"
PORT = 18791  # never 18700


def utc_now():
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def db_counts(db_path: Path) -> dict:
    if not db_path.exists():
        return {}
    con = sqlite3.connect(str(db_path))
    cur = con.cursor()
    out = {}
    for table in (
        "incoming_payments",
        "incoming_payment_allocations",
        "ar_settlement_allocs",
        "bank_cash_movements",
        "journal_entries",
        "journal_entry_lines",
        "ar_invoices",
        "sales_orders",
        "stock_moves",
        "inventory_balances",
    ):
        try:
            cur.execute(f"SELECT COUNT(*) FROM {table}")
            out[table] = int(cur.fetchone()[0])
        except Exception:
            out[table] = None
    try:
        cur.execute("SELECT COALESCE(SUM(open_amount),0) FROM ar_invoices")
        out["ar_open_sum"] = int(cur.fetchone()[0])
    except Exception:
        out["ar_open_sum"] = None
    try:
        cur.execute("SELECT COALESCE(SUM(balance),0) FROM bank_accounts")
        out["bank_balance_sum"] = int(cur.fetchone()[0])
    except Exception:
        out["bank_balance_sum"] = None
    con.close()
    return out


def req(opener, method, url, body=None, headers=None, timeout=8):
    data = None
    hdrs = dict(headers or {})
    if body is not None:
        if isinstance(body, (dict, list)):
            data = json.dumps(body).encode()
            hdrs.setdefault("Content-Type", "application/json")
        elif isinstance(body, str):
            data = body.encode()
            hdrs.setdefault("Content-Type", "application/json")
        else:
            data = body
    r = urllib.request.Request(url, data=data, headers=hdrs, method=method)
    try:
        with opener.open(r, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8", "replace")
            return resp.status, raw, dict(resp.headers)
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace")
        return e.code, raw, dict(e.headers)
    except Exception as e:
        return 0, str(e), {}


def assert_json(raw: str):
    if raw is None or raw == "":
        return None, "empty body"
    try:
        return json.loads(raw), None
    except Exception as e:
        return None, f"invalid json: {e}"


def looks_leaky(raw: str) -> bool:
    low = (raw or "").lower()
    needles = ["stacktrace", "at java.", "sqlite error", "password=", "secret=", "begin transaction"]
    return any(n in low for n in needles)


def main() -> int:
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    out = ROOT / "build" / "erp" / f"http-neg-gate-{stamp}"
    out.mkdir(parents=True, exist_ok=True)
    db = out / "http-neg.db"
    log = out / "server.log"
    for p in (db, Path(str(db) + "-shm"), Path(str(db) + "-wal")):
        try:
            p.unlink()
        except FileNotFoundError:
            pass

    env = os.environ.copy()
    env["PATH"] = "/usr/bin:/bin:/usr/sbin:/sbin:/opt/homebrew/bin"
    env["DATABASE_URL"] = f"sqlite:{db}"
    env["ERP_LISTEN_HOST"] = "127.0.0.1"
    env["ERP_LISTEN_PORT"] = str(PORT)
    env["ERP_DEMO_MODE"] = "1"
    env["JNATIVE_GC"] = "generational"
    env.pop("ERP_LISTEN", None)

    proc = subprocess.Popen(
        [str(BIN)],
        cwd=str(ROOT),
        env=env,
        stdout=log.open("w"),
        stderr=subprocess.STDOUT,
    )
    cases = []
    base = f"http://127.0.0.1:{PORT}"
    cookie_path = str(out / "cookies.txt")
    try:
        # wait ready
        ready = False
        csrf = ""
        opener = None
        for _ in range(80):
            if proc.poll() is not None:
                break
            cj = http.cookiejar.MozillaCookieJar(cookie_path)
            opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cj))
            code, raw, _ = req(opener, "POST", f"{base}/api/auth/demo-enter", {})
            if code == 200:
                obj, err = assert_json(raw)
                if obj and obj.get("csrfToken"):
                    csrf = obj["csrfToken"]
                    cj.save(ignore_discard=True, ignore_expires=True)
                    ready = True
                    break
            time.sleep(0.4)
        if not ready:
            cases.append({"id": "boot", "status": "FAIL", "detail": "server not ready", "http": None})
            raise SystemExit(1)

        def add(cid, status, **kw):
            row = {"id": cid, "status": status}
            row.update(kw)
            cases.append(row)
            print(f"{status:4} {cid}: {kw.get('detail','')}", flush=True)

        # snapshot after boot/seed
        before = db_counts(db)

        # 1) unauthenticated
        bare = urllib.request.build_opener()
        code, raw, _ = req(bare, "GET", f"{base}/api/erp/partners?kind=CUSTOMER")
        obj, err = assert_json(raw)
        ok = code in (401, 403) and obj is not None and not looks_leaky(raw) and (
            obj.get("code") in ("AUTH_REQUIRED", "FORBIDDEN", "UNAUTHORIZED") or "message" in obj
        )
        add("unauth_partners", "PASS" if ok else "FAIL", http=code, detail=err or obj.get("code") if obj else raw[:80])

        def is_business_error(http_code, obj):
            """Project API often returns HTTP 200 with ok=0 / code / message for domain errors."""
            if obj is None:
                return False
            if http_code >= 400:
                return True
            if obj.get("ok") == 0:
                return True
            code = obj.get("code")
            if isinstance(code, str) and code and code not in ("OK", "ALREADY_POSTED"):
                return True
            msg = obj.get("message")
            if isinstance(msg, str) and msg and obj.get("ok") != 1:
                # Heuristic: error-ish messages without success flag
                bad_hints = ("required", "exceed", "invalid", "forbidden", "not found", "失败", "必须", "超额", "不足")
                low = msg.lower()
                if any(h in low or h in msg for h in bad_hints):
                    return True
            return False

        # 2) malformed JSON — must be complete JSON error, no leak, no success post
        code, raw, _ = req(
            opener,
            "POST",
            f"{base}/api/erp/finance/incoming-payments",
            "{",
            headers={"X-CSRF-Token": csrf},
        )
        obj, err = assert_json(raw)
        ok = obj is not None and not looks_leaky(raw) and is_business_error(code, obj)
        add("malformed_json", "PASS" if ok else "FAIL", http=code, detail=(obj or {}).get("code") if obj else err)

        # 3) missing required fields
        code, raw, _ = req(
            opener,
            "POST",
            f"{base}/api/erp/finance/incoming-payments",
            {"idempotencyKey": "http-neg-missing"},
            headers={"X-CSRF-Token": csrf, "Idempotency-Key": "http-neg-missing"},
        )
        obj, err = assert_json(raw)
        ok = obj is not None and not looks_leaky(raw) and is_business_error(code, obj)
        add("missing_fields", "PASS" if ok else "FAIL", http=code, detail=(obj or {}).get("code") or (obj or {}).get("message") or err)

        # 4) over-allocation / nonexistent invoice
        code, raw, _ = req(
            opener,
            "POST",
            f"{base}/api/erp/finance/incoming-payments",
            {
                "customerId": 1,
                "bankAccountId": 1,
                "paymentDate": "2026-10-16",
                "currency": "CNY",
                "amountCents": 1,
                "amount": 1,
                "idempotencyKey": "http-neg-over",
                "allocations": [{"invoiceId": 999999, "amountCents": 999999999, "allocatedAmount": 999999999}],
            },
            headers={"X-CSRF-Token": csrf, "Idempotency-Key": "http-neg-over"},
        )
        obj, err = assert_json(raw)
        ok = obj is not None and not looks_leaky(raw) and is_business_error(code, obj)
        add("over_or_missing_invoice", "PASS" if ok else "FAIL", http=code, detail=(obj or {}).get("message") or (obj or {}).get("code") or err)

        # 5) idempotency key with different bodies should not silently succeed both as same post
        body_a = {
            "customerId": 1,
            "bankAccountId": 1,
            "paymentDate": "2026-10-16",
            "amount": 1,
            "idempotencyKey": "http-neg-idem-diff",
            "allocations": [],
        }
        body_b = dict(body_a)
        body_b["amount"] = 2
        code1, raw1, _ = req(
            opener,
            "POST",
            f"{base}/api/erp/finance/incoming-payments",
            body_a,
            headers={"X-CSRF-Token": csrf, "Idempotency-Key": "http-neg-idem-diff"},
        )
        code2, raw2, _ = req(
            opener,
            "POST",
            f"{base}/api/erp/finance/incoming-payments",
            body_b,
            headers={"X-CSRF-Token": csrf, "Idempotency-Key": "http-neg-idem-diff"},
        )
        o1, e1 = assert_json(raw1)
        o2, e2 = assert_json(raw2)
        # Accept: second is error OR same cached result with same amount semantics; reject empty/truncated
        ok = o1 is not None and o2 is not None and not looks_leaky(raw1 + raw2)
        # If first succeeded posting amount=1, second must not create a second payment with amount=2
        after_idem = db_counts(db)
        ip_delta = None
        if before.get("incoming_payments") is not None and after_idem.get("incoming_payments") is not None:
            ip_delta = after_idem["incoming_payments"] - before["incoming_payments"]
            ok = ok and ip_delta <= 1
        add(
            "idempotency_diff_body",
            "PASS" if ok else "FAIL",
            http=[code1, code2],
            detail=f"ip_delta={ip_delta}",
        )

        # 6) nonexistent document GET
        code, raw, _ = req(opener, "GET", f"{base}/api/erp/salesOrders/999999999")
        obj, err = assert_json(raw)
        ok = code in (404, 400, 403) and obj is not None and not looks_leaky(raw)
        add("missing_sales_order", "PASS" if ok else "FAIL", http=code, detail=(obj or {}).get("code") or err)

        # 7) repeat negative over-alloc 10x — always parseable, no partial bank/GL growth beyond first failure
        snap = db_counts(db)
        ok_n = 0
        for i in range(10):
            code, raw, _ = req(
                opener,
                "POST",
                f"{base}/api/erp/finance/incoming-payments",
                {
                    "customerId": 1,
                    "bankAccountId": 1,
                    "paymentDate": "2026-10-16",
                    "amount": 1,
                    "idempotencyKey": f"http-neg-rep-{i}",
                    "allocations": [{"invoiceId": 1, "allocatedAmount": 999999999}],
                },
                headers={"X-CSRF-Token": csrf, "Idempotency-Key": f"http-neg-rep-{i}"},
            )
            obj, err = assert_json(raw)
            if obj is not None and not looks_leaky(raw) and code != 0:
                ok_n += 1
        after = db_counts(db)
        # failed over-alloc must not increase bank movements / journals unexpectedly vs snap
        # (allow 0 growth; if growth, mark FAIL for partial write suspicion)
        bcm_growth = 0
        je_growth = 0
        if snap.get("bank_cash_movements") is not None:
            bcm_growth = after["bank_cash_movements"] - snap["bank_cash_movements"]
        if snap.get("journal_entries") is not None:
            je_growth = after["journal_entries"] - snap["journal_entries"]
        ok = ok_n == 10 and bcm_growth == 0 and je_growth == 0
        add(
            "repeat_overalloc_no_partial",
            "PASS" if ok else "FAIL",
            detail=f"json_ok={ok_n}/10 bcm_growth={bcm_growth} je_growth={je_growth}",
        )

        # 8) concurrent malformed + auth spam — all JSON
        import concurrent.futures

        def one(i):
            c, r, _ = req(opener, "POST", f"{base}/api/erp/finance/incoming-payments", "{", headers={"X-CSRF-Token": csrf})
            o, e = assert_json(r)
            return c != 0 and o is not None and not looks_leaky(r)

        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as ex:
            flags = list(ex.map(one, range(16)))
        add("concurrent_malformed", "PASS" if all(flags) else "FAIL", detail=f"ok={sum(flags)}/16")

    finally:
        try:
            proc.send_signal(signal.SIGTERM)
            proc.wait(timeout=5)
        except Exception:
            try:
                proc.kill()
            except Exception:
                pass

    passed = sum(1 for c in cases if c["status"] == "PASS")
    failed = sum(1 for c in cases if c["status"] != "PASS")
    report = {
        "generated_at": utc_now(),
        "port": PORT,
        "db": str(db),
        "cases": cases,
        "summary": {"total": len(cases), "pass": passed, "fail": failed},
        "verdict": "PASS" if failed == 0 and passed > 0 else "FAIL",
        "final_db_counts": db_counts(db),
        "notes": [
            "Does not use port 18700.",
            "Cookies/tokens are not written into the report JSON (only presence checked in-process).",
        ],
    }
    (out / "report.json").write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n")
    md = [
        "# ERP HTTP Negative Gate",
        "",
        f"- Generated: `{report['generated_at']}`",
        f"- Port: `{PORT}` (18700 forbidden)",
        f"- Verdict: `{report['verdict']}` {passed}/{len(cases)}",
        "",
        "| id | status | http | detail |",
        "|----|--------|------|--------|",
    ]
    for c in cases:
        md.append(f"| {c['id']} | {c['status']} | {c.get('http')} | {str(c.get('detail',''))[:100]} |")
    (out / "report.md").write_text("\n".join(md) + "\n")
    print(f"REPORT {out / 'report.md'}")
    print(f"VERDICT {report['verdict']} {passed}/{len(cases)}")
    return 0 if report["verdict"] == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
