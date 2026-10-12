/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

// Author: 张晓达
import * as path from 'path';
import * as vscode from 'vscode';

export interface JsonDiagnostic {
  file: string;
  line: number;
  column: number;
  severity: string;
  message: string;
  code?: string;
  hint?: string;
}

export interface BuildReport {
  success: boolean;
  artifact: string | null;
  diagnostics: JsonDiagnostic[];
  target?: string;
  compilerVersion?: string;
}

/** Build diagnostics keyed by project root — cleared only on successful build of that project. */
const buildCollections = new Map<string, vscode.DiagnosticCollection>();

export function getBuildCollection(projectRoot: string): vscode.DiagnosticCollection {
  const key = path.resolve(projectRoot);
  let c = buildCollections.get(key);
  if (!c) {
    c = vscode.languages.createDiagnosticCollection(`jnative-build:${key}`);
    buildCollections.set(key, c);
  }
  return c;
}

export function clearProjectDiagnostics(projectRoot: string): void {
  const c = buildCollections.get(path.resolve(projectRoot));
  c?.clear();
}

export function disposeAllDiagnostics(): void {
  for (const c of buildCollections.values()) {
    c.dispose();
  }
  buildCollections.clear();
}

export function parseBuildReport(stdout: string): BuildReport | undefined {
  const text = stdout.trim();
  if (!text) {
    return undefined;
  }
  if (!text.startsWith('{')) {
    const lines = text.split(/\r?\n/).filter((l) => l.trim().startsWith('{') && l.trim().endsWith('}'));
    if (!lines.length) {
      // Also accept last JSON object spanning trailing lines
      const lastBrace = text.lastIndexOf('\n{');
      if (lastBrace >= 0) {
        return parseBuildReport(text.slice(lastBrace + 1));
      }
      return undefined;
    }
    return parseBuildReport(lines[lines.length - 1]);
  }
  try {
    const obj = JSON.parse(text) as BuildReport;
    if (typeof obj.success !== 'boolean') {
      return undefined;
    }
    if (!Array.isArray(obj.diagnostics)) {
      obj.diagnostics = [];
    }
    return obj;
  } catch {
    return undefined;
  }
}

/**
 * Apply structured build diagnostics.
 * Compiler line/column are 1-based. Tooling errors without a real file are shown
 * in the Output channel only (via caller) — we skip forging user-file:1:1.
 */
export function applyJsonDiagnostics(
  projectRoot: string,
  diags: JsonDiagnostic[],
  fallbackCwd?: string
): { applied: number; orphanMessages: string[] } {
  const collection = getBuildCollection(projectRoot);
  collection.clear();
  const byFile = new Map<string, vscode.Diagnostic[]>();
  const orphanMessages: string[] = [];
  let applied = 0;
  for (const d of diags) {
    if (!d || !d.message) {
      continue;
    }
    const rawFile = d.file || '';
    if (
      !rawFile ||
      rawFile === '<unknown>' ||
      rawFile === '<stdin>' ||
      ((d.line || 0) <= 0 && (rawFile === '<stdlib>' || rawFile === '<unknown>'))
    ) {
      orphanMessages.push(d.message);
      continue;
    }
    let file = rawFile;
    if (file === '<stdlib>') {
      // Keep visible but do not map onto user Main.java:1
      orphanMessages.push(`[stdlib] ${d.message}`);
      continue;
    }
    if (!path.isAbsolute(file)) {
      file = path.resolve(fallbackCwd || projectRoot, file);
    }
    const severity = toSeverity(d.severity);
    const line = Math.max(0, (d.line || 1) - 1);
    const col = Math.max(0, (d.column || 1) - 1);
    const range = new vscode.Range(line, col, line, Math.max(col + 1, col));
    let message = d.message || '';
    if (d.hint) {
      message = `${message}\nhelp: ${d.hint}`;
    }
    const vs = new vscode.Diagnostic(range, message, severity);
    vs.source = 'jnative';
    if (d.code) {
      vs.code = d.code;
    }
    const list = byFile.get(file) ?? [];
    list.push(vs);
    byFile.set(file, list);
    applied++;
  }
  for (const [file, list] of byFile) {
    collection.set(vscode.Uri.file(file), list);
  }
  return { applied, orphanMessages };
}

function toSeverity(s: string): vscode.DiagnosticSeverity {
  switch ((s || '').toLowerCase()) {
    case 'error':
      return vscode.DiagnosticSeverity.Error;
    case 'warning':
      return vscode.DiagnosticSeverity.Warning;
    default:
      return vscode.DiagnosticSeverity.Hint;
  }
}

/** Parse human diagnostic lines as fallback when JSON is absent. */
export function parseTextDiagnostics(text: string): JsonDiagnostic[] {
  const re = /^(.+):(\d+):(\d+):\s*(error|warning|note):\s*(?:\[([A-Z0-9]+)\]\s*)?(.*)$/gim;
  const out: JsonDiagnostic[] = [];
  let m: RegExpExecArray | null;
  while ((m = re.exec(text)) !== null) {
    out.push({
      file: m[1],
      line: parseInt(m[2], 10),
      column: parseInt(m[3], 10),
      severity: m[4].toLowerCase(),
      code: m[5] || undefined,
      message: m[6],
    });
  }
  return out;
}
