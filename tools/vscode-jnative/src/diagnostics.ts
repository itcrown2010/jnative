// Author: 张晓达
import * as path from 'path';
import * as vscode from 'vscode';

export interface JsonDiagnostic {
  file: string;
  line: number;
  column: number;
  severity: string;
  message: string;
}

export interface BuildReport {
  success: boolean;
  artifact: string | null;
  diagnostics: JsonDiagnostic[];
}

const collections = new Map<string, vscode.DiagnosticCollection>();

export function getCollection(projectRoot: string): vscode.DiagnosticCollection {
  const key = path.resolve(projectRoot);
  let c = collections.get(key);
  if (!c) {
    c = vscode.languages.createDiagnosticCollection(`jnative:${key}`);
    collections.set(key, c);
  }
  return c;
}

export function clearProjectDiagnostics(projectRoot: string): void {
  const c = collections.get(path.resolve(projectRoot));
  c?.clear();
}

export function disposeAllDiagnostics(): void {
  for (const c of collections.values()) {
    c.dispose();
  }
  collections.clear();
}

export function parseBuildReport(stdout: string): BuildReport | undefined {
  const text = stdout.trim();
  if (!text.startsWith('{')) {
    // Try last JSON object line
    const lines = text.split(/\r?\n/).filter((l) => l.trim().startsWith('{'));
    if (!lines.length) {
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

/** Apply structured diagnostics; line/column from compiler are 1-based. */
export function applyJsonDiagnostics(
  projectRoot: string,
  diags: JsonDiagnostic[],
  fallbackCwd?: string
): void {
  const collection = getCollection(projectRoot);
  collection.clear();
  const byFile = new Map<string, vscode.Diagnostic[]>();
  for (const d of diags) {
    if (!d || !d.message) {
      continue;
    }
    let file = d.file || '';
    if (file === '<stdlib>' || file === '<unknown>') {
      // Annotate without wrong user mapping
      file = path.join(projectRoot, '.jnative', 'stdlib-diagnostic');
    } else if (!path.isAbsolute(file)) {
      file = path.resolve(fallbackCwd || projectRoot, file);
    }
    const severity = toSeverity(d.severity);
    const line = Math.max(0, (d.line || 1) - 1);
    const col = Math.max(0, (d.column || 1) - 1);
    const range = new vscode.Range(line, col, line, Math.max(col + 1, col));
    const vs = new vscode.Diagnostic(range, d.message, severity);
    vs.source = 'jnative';
    if (d.file === '<stdlib>') {
      vs.message = `[stdlib] ${d.message}`;
    }
    const list = byFile.get(file) ?? [];
    list.push(vs);
    byFile.set(file, list);
  }
  for (const [file, list] of byFile) {
    collection.set(vscode.Uri.file(file), list);
  }
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
  const re = /^(.+):(\d+):(\d+):\s*(error|warning|note):\s*(.*)$/gim;
  const out: JsonDiagnostic[] = [];
  let m: RegExpExecArray | null;
  while ((m = re.exec(text)) !== null) {
    out.push({
      file: m[1],
      line: parseInt(m[2], 10),
      column: parseInt(m[3], 10),
      severity: m[4].toLowerCase(),
      message: m[5],
    });
  }
  return out;
}
