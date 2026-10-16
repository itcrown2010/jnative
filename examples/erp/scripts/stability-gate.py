#!/usr/bin/env python3
# Author: 张晓达
"""ERP stability acceptance gate.

Isolated SQLite DBs per suite/round. Never binds 18700. Emits JSON + Markdown.
Exit non-zero on crash, timeout, or assertion failure.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import signal
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
ERP = ROOT / "examples" / "erp"
BIN_DEFAULT = ERP / ".jnative" / "build" / "erp-server"
JAR = ROOT / "target" / "jnative.jar"

SUITES = [
    ("logic", "--logic-test"),
    ("sales", "--sales-test"),
    ("finance", "--finance-test"),
    ("settlement", "--settlement-test"),
    ("void", "--void-test"),
    ("recon", "--recon-test"),
    ("costing", "--costing-test"),
    ("gl", "--gl-test"),
    ("enterprise", "--enterprise-test"),
    ("integration", "--integration-test"),
    ("manufacturing", "--manufacturing-test"),
    ("pilot", "--pilot-test"),
    ("workflow", "--workflow-uat-test"),
]

CRASH_RE = re.compile(
    r"(signal\s*=?\s*11|SIGSEGV|EXC_BAD_ACCESS|JNative GC2:|signal\s*=?\s*6\b)",
    re.I,
)
FAIL_RE = re.compile(r"^FAIL\b|Build FAILED|SCHEMA_NOT_READY", re.M)


def utc_now() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def run_capture(cmd, cwd=None, env=None, timeout=None):
    try:
        p = subprocess.run(
            cmd,
            cwd=cwd,
            env=env,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            timeout=timeout,
        )
        return p.returncode, p.stdout or ""
    except subprocess.TimeoutExpired as e:
        out = e.stdout or ""
        if isinstance(out, bytes):
            out = out.decode("utf-8", "replace")
        return 124, out + "\n[TIMEOUT]\n"


def git_meta(root: Path) -> dict:
    meta = {
        "commit": "unknown",
        "branch": "unknown",
        "dirty": False,
        "diff_stat": "",
        "diff_stat_redacted": True,
    }
    ec, out = run_capture(["git", "rev-parse", "HEAD"], cwd=str(root), timeout=30)
    if ec == 0:
        meta["commit"] = out.strip()
    ec, out = run_capture(["git", "rev-parse", "--abbrev-ref", "HEAD"], cwd=str(root), timeout=30)
    if ec == 0:
        meta["branch"] = out.strip()
    ec, out = run_capture(["git", "status", "--porcelain"], cwd=str(root), timeout=60)
    if ec == 0:
        lines = [ln for ln in out.splitlines() if ln.strip()]
        meta["dirty"] = len(lines) > 0
        # path-only summary; never dump file contents
        meta["changed_paths"] = [ln[3:] for ln in lines[:200]]
        meta["changed_path_count"] = len(lines)
    ec, out = run_capture(["git", "diff", "--stat", "HEAD"], cwd=str(root), timeout=60)
    if ec == 0:
        # strip absolute paths that might appear; keep counts only
        meta["diff_stat"] = "\n".join(out.splitlines()[:80])
    return meta


def compiler_meta(jar: Path, bin_path: Path) -> dict:
    meta = {"jar": str(jar), "jar_sha256": None, "bin": str(bin_path), "bin_mtime": None, "version": None}
    if jar.is_file():
        h = hashlib.sha256()
        with jar.open("rb") as f:
            for chunk in iter(lambda: f.read(1 << 20), b""):
                h.update(chunk)
        meta["jar_sha256"] = h.hexdigest()
        ec, out = run_capture(["java", "-jar", str(jar), "--version"], timeout=30)
        if ec == 0:
            meta["version"] = out.strip().splitlines()[0][:200] if out.strip() else None
        else:
            meta["version"] = "unparsed"
    if bin_path.is_file():
        meta["bin_mtime"] = datetime.fromtimestamp(bin_path.stat().st_mtime, timezone.utc).strftime(
            "%Y-%m-%dT%H:%M:%SZ"
        )
        meta["bin_size"] = bin_path.stat().st_size
    return meta


def ensure_binary(root: Path, bin_path: Path, rebuild: bool) -> None:
    if bin_path.is_file() and not rebuild:
        return
    proj = root / "examples" / "erp" / "jnative.project"
    bak = root / "examples" / "erp" / "jnative.project.gatebak"
    server_only = root / "examples" / "erp" / "jnative.project.server-only"
    if not server_only.is_file():
        raise SystemExit("missing jnative.project.server-only")
    shutil.copy2(proj, bak)
    try:
        shutil.copy2(server_only, proj)
        ec, out = run_capture(
            ["java", "-jar", str(JAR), "build", "--project", "examples/erp", "--target", "native"],
            cwd=str(root),
            timeout=300,
        )
        (root / "build" / "erp" / "stability-gate" / "rebuild.log").write_text(out)
        if ec != 0 or not bin_path.is_file():
            raise SystemExit(f"erp-server build failed exit={ec}")
    finally:
        if bak.is_file():
            shutil.copy2(bak, proj)
            bak.unlink(missing_ok=True)


def classify_result(exit_code: int, log_text: str, timed_out: bool) -> dict:
    crash = bool(CRASH_RE.search(log_text)) or (exit_code >= 128 and not timed_out)
    fail_assert = bool(FAIL_RE.search(log_text)) or (exit_code not in (0, 124) and not crash)
    if timed_out:
        status = "TIMEOUT"
    elif crash:
        status = "CRASH"
    elif exit_code != 0 or fail_assert:
        status = "FAIL"
    else:
        status = "PASS"
    # redact secrets-ish lines
    keys = []
    for m in CRASH_RE.finditer(log_text):
        keys.append(m.group(0))
    for m in re.finditer(r"^(FAIL[^\n]{0,160})", log_text, re.M):
        keys.append(m.group(1))
    return {
        "status": status,
        "crash": crash,
        "timed_out": timed_out,
        "key_errors": keys[:20],
    }


def run_suite(
    bin_path: Path,
    out_dir: Path,
    name: str,
    flag: str,
    round_i: int,
    gc_mode: str,
    young_size: int | None,
    timeout_s: int,
    keep_success: bool,
) -> dict:
    db = out_dir / "dbs" / f"{name}-r{round_i}.db"
    log = out_dir / "logs" / f"{name}-r{round_i}.log"
    db.parent.mkdir(parents=True, exist_ok=True)
    log.parent.mkdir(parents=True, exist_ok=True)
    for p in (db, Path(str(db) + "-shm"), Path(str(db) + "-wal")):
        try:
            p.unlink()
        except FileNotFoundError:
            pass

    env = os.environ.copy()
    env["PATH"] = "/usr/bin:/bin:/usr/sbin:/sbin:/opt/homebrew/bin"
    env["DATABASE_URL"] = f"sqlite:{db}"
    env["JNATIVE_GC"] = gc_mode
    # never touch 18700
    env["ERP_LISTEN_PORT"] = "0"
    env.pop("ERP_LISTEN", None)
    if young_size:
        env["JNATIVE_YOUNG_SIZE"] = str(young_size)
    else:
        env.pop("JNATIVE_YOUNG_SIZE", None)
    env.pop("JNATIVE_GC_STRESS", None)

    started = utc_now()
    t0 = time.monotonic()
    timed_out = False
    try:
        p = subprocess.Popen(
            [str(bin_path), flag],
            cwd=str(ROOT),
            env=env,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
        )
        try:
            out, _ = p.communicate(timeout=timeout_s)
            ec = p.returncode if p.returncode is not None else 1
        except subprocess.TimeoutExpired:
            timed_out = True
            try:
                p.send_signal(signal.SIGTERM)
                try:
                    out, _ = p.communicate(timeout=5)
                except subprocess.TimeoutExpired:
                    p.kill()
                    out, _ = p.communicate(timeout=5)
            except Exception:
                out = ""
            ec = 124
    except Exception as e:
        out = f"spawn failed: {e}"
        ec = 1
    elapsed_ms = int((time.monotonic() - t0) * 1000)
    log.write_text(out or "")
    info = classify_result(ec, out or "", timed_out)
    # cleanup success artifacts if requested
    if info["status"] == "PASS" and not keep_success:
        for p in (db, Path(str(db) + "-shm"), Path(str(db) + "-wal"), log):
            try:
                p.unlink()
            except FileNotFoundError:
                pass
        db_kept = False
        log_kept = False
    else:
        db_kept = db.exists()
        log_kept = log.exists()

    return {
        "suite": name,
        "flag": flag,
        "round": round_i,
        "started_at": started,
        "elapsed_ms": elapsed_ms,
        "exit_code": ec,
        "timeout_s": timeout_s,
        "timed_out": timed_out,
        "status": info["status"],
        "crash": info["crash"],
        "key_errors": info["key_errors"],
        "db_kept": db_kept,
        "log_kept": log_kept,
        "log_rel": str(log.relative_to(out_dir)) if log_kept else None,
        "db_rel": str(db.relative_to(out_dir)) if db_kept else None,
        "gc_mode": gc_mode,
        "young_size": young_size,
    }


def write_reports(out_dir: Path, report: dict) -> None:
    (out_dir / "report.json").write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n")
    lines = []
    lines.append("# ERP Stability Gate Report")
    lines.append("")
    lines.append(f"- Generated: `{report['generated_at']}`")
    lines.append(f"- Commit: `{report['git']['commit']}` branch `{report['git']['branch']}` dirty={report['git']['dirty']}")
    lines.append(f"- Changed paths: {report['git'].get('changed_path_count', 0)}")
    lines.append(f"- Compiler jar sha256: `{report['compiler'].get('jar_sha256')}`")
    lines.append(f"- Binary mtime: `{report['compiler'].get('bin_mtime')}` size={report['compiler'].get('bin_size')}")
    lines.append(f"- GC mode: `{report['config']['gc_mode']}` young_size={report['config'].get('young_size')}")
    lines.append(f"- Rounds: {report['config']['rounds']} timeout_s={report['config']['timeout_s']}")
    lines.append("")
    s = report.get("summary") or {}
    lines.append("## Summary")
    lines.append("")
    lines.append(f"| total | pass | fail | crash | timeout |")
    lines.append(f"|------:|-----:|-----:|------:|--------:|")
    lines.append(
        f"| {s.get('total', 0)} | {s.get('pass', 0)} | {s.get('fail', 0)} | {s.get('crash', 0)} | {s.get('timeout', 0)} |"
    )
    lines.append("")
    lines.append(f"**Verdict:** `{report['verdict']}`  final_exit={report['final_exit']}")
    lines.append("")
    lines.append("## Runs")
    lines.append("")
    lines.append("| suite | round | status | exit | ms | errors |")
    lines.append("|-------|------:|--------|-----:|---:|--------|")
    for r in report["runs"]:
        err = "; ".join(r.get("key_errors") or [])[:120].replace("|", "/")
        lines.append(
            f"| {r['suite']} | {r['round']} | {r['status']} | {r['exit_code']} | {r['elapsed_ms']} | {err} |"
        )
    lines.append("")
    if report.get("failures"):
        lines.append("## Failures (kept artifacts)")
        lines.append("")
        for f in report["failures"]:
            lines.append(
                f"- `{f['suite']}` r{f['round']} status={f['status']} log=`{f.get('log_rel')}` db=`{f.get('db_rel')}`"
            )
        lines.append("")
    (out_dir / "report.md").write_text("\n".join(lines) + "\n")


def finalize(report: dict, out_dir: Path) -> int:
    total = len(report["runs"])
    passed = sum(1 for x in report["runs"] if x["status"] == "PASS")
    crashed = sum(1 for x in report["runs"] if x["status"] == "CRASH")
    timed = sum(1 for x in report["runs"] if x["status"] == "TIMEOUT")
    failed = sum(1 for x in report["runs"] if x["status"] == "FAIL")
    report["summary"] = {
        "total": total,
        "pass": passed,
        "fail": failed,
        "crash": crashed,
        "timeout": timed,
    }
    report["failures"] = [x for x in report["runs"] if x["status"] != "PASS"]
    ok = crashed == 0 and timed == 0 and failed == 0 and passed == total and total > 0
    report["verdict"] = "PASS" if ok else "FAIL"
    report["final_exit"] = 0 if ok else 1
    report["generated_at"] = utc_now()
    write_reports(out_dir, report)
    print(f"\nREPORT {out_dir / 'report.md'}", flush=True)
    print(f"JSON   {out_dir / 'report.json'}", flush=True)
    print(f"VERDICT {report['verdict']} {passed}/{total}", flush=True)
    return report["final_exit"]


def main() -> int:
    ap = argparse.ArgumentParser(description="ERP stability gate")
    ap.add_argument("--rounds", type=int, default=10)
    ap.add_argument("--timeout", type=int, default=180, help="per-suite timeout seconds")
    ap.add_argument("--gc", default="generational", help="JNATIVE_GC mode")
    ap.add_argument("--young-size", type=int, default=0, help="optional JNATIVE_YOUNG_SIZE")
    ap.add_argument("--keep-success", action="store_true")
    ap.add_argument("--rebuild", action="store_true")
    ap.add_argument("--suites", default="", help="comma names subset")
    ap.add_argument("--out", default="", help="output dir under build/erp/")
    args = ap.parse_args()

    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    out_dir = Path(args.out) if args.out else ROOT / "build" / "erp" / f"stability-gate-{stamp}"
    out_dir.mkdir(parents=True, exist_ok=True)

    ensure_binary(ROOT, BIN_DEFAULT, args.rebuild)
    selected = SUITES
    if args.suites.strip():
        want = {x.strip() for x in args.suites.split(",") if x.strip()}
        selected = [s for s in SUITES if s[0] in want]
        if not selected:
            print("no suites selected", file=sys.stderr)
            return 2

    young = args.young_size if args.young_size > 0 else None
    report = {
        "generated_at": utc_now(),
        "git": git_meta(ROOT),
        "compiler": compiler_meta(JAR, BIN_DEFAULT),
        "config": {
            "rounds": args.rounds,
            "timeout_s": args.timeout,
            "gc_mode": args.gc,
            "young_size": young,
            "keep_success": args.keep_success,
            "suites": [s[0] for s in selected],
            "forbid_port": 18700,
            "isolated_sqlite": True,
        },
        "runs": [],
        "failures": [],
        "summary": {},
        "verdict": "FAIL",
        "final_exit": 1,
        "notes": [
            "Does not bind ERP_LISTEN_PORT=18700.",
            "Does not modify existing business DBs.",
            "Secrets (passwords/cookies/tokens/full DB URLs) are not recorded; only sqlite file paths under out_dir.",
        ],
    }

    def _on_signal(signum, frame):
        report["interrupted_by_signal"] = int(signum)
        try:
            finalize(report, out_dir)
        finally:
            os._exit(1)

    signal.signal(signal.SIGTERM, _on_signal)
    signal.signal(signal.SIGINT, _on_signal)

    print(
        f"GATE start suites={len(selected)} rounds={args.rounds} out={out_dir}",
        flush=True,
    )
    for r in range(1, args.rounds + 1):
        for name, flag in selected:
            print(f"RUN {name} r{r} {flag}", flush=True)
            try:
                row = run_suite(
                    BIN_DEFAULT,
                    out_dir,
                    name,
                    flag,
                    r,
                    args.gc,
                    young,
                    args.timeout,
                    args.keep_success,
                )
            except Exception as ex:
                row = {
                    "suite": name,
                    "flag": flag,
                    "round": r,
                    "started_at": utc_now(),
                    "elapsed_ms": 0,
                    "exit_code": 1,
                    "timeout_s": args.timeout,
                    "timed_out": False,
                    "status": "FAIL",
                    "crash": False,
                    "key_errors": [f"runner_exception:{type(ex).__name__}:{ex}"],
                    "db_kept": False,
                    "log_kept": False,
                    "log_rel": None,
                    "db_rel": None,
                    "gc_mode": args.gc,
                    "young_size": young,
                }
            report["runs"].append(row)
            print(
                f"{row['status']:7} {name:14} r{r} exit={row['exit_code']} ms={row['elapsed_ms']}",
                flush=True,
            )
            try:
                runs = report["runs"]
                write_reports(
                    out_dir,
                    {
                        **report,
                        "summary": {
                            "total": len(runs),
                            "pass": sum(1 for x in runs if x["status"] == "PASS"),
                            "fail": sum(1 for x in runs if x["status"] == "FAIL"),
                            "crash": sum(1 for x in runs if x["status"] == "CRASH"),
                            "timeout": sum(1 for x in runs if x["status"] == "TIMEOUT"),
                            "partial": True,
                        },
                        "verdict": "PARTIAL",
                        "final_exit": 1,
                    },
                )
            except Exception as ex:
                print(f"checkpoint write failed: {ex}", flush=True)

    return finalize(report, out_dir)


if __name__ == "__main__":
    sys.exit(main())
