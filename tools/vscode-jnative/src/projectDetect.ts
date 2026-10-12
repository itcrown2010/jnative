/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

// Author: 张晓达
import * as fs from 'fs';
import * as path from 'path';

export interface JNativeProjectInfo {
  root: string;
  name: string;
  sourceRoots: string[];
  mainClass: string;
  hasProjectFile: boolean;
  hasToml: boolean;
}

/**
 * A directory is a JNative project root only if it has `jnative.project`,
 * or both `jnative.toml` and a `src` tree.
 */
export function isProjectRoot(dir: string): boolean {
  if (fs.existsSync(path.join(dir, 'jnative.project'))) {
    return true;
  }
  if (fs.existsSync(path.join(dir, 'jnative.toml')) && fs.existsSync(path.join(dir, 'src'))) {
    return true;
  }
  return false;
}

export function loadProject(root: string): JNativeProjectInfo | undefined {
  if (!isProjectRoot(root)) {
    return undefined;
  }
  const projectFile = path.join(root, 'jnative.project');
  let name = path.basename(root);
  let sourceRoots = ['src'];
  let mainClass = 'Main';
  const hasProjectFile = fs.existsSync(projectFile);
  const hasToml = fs.existsSync(path.join(root, 'jnative.toml'));
  if (hasProjectFile) {
    try {
      const text = fs.readFileSync(projectFile, 'utf8');
      const nameM = text.match(/"name"\s*:\s*"([^"]+)"/);
      if (nameM) {
        name = nameM[1];
      }
      const mainM = text.match(/"main"\s*:\s*"([^"]+)"/);
      if (mainM) {
        mainClass = mainM[1];
      }
      const srcM = text.match(/"source"\s*:\s*\[([^\]]*)\]/);
      if (srcM) {
        const roots = [...srcM[1].matchAll(/"([^"]+)"/g)].map((m) => m[1]);
        if (roots.length) {
          sourceRoots = roots;
        }
      }
      const moduleSrcs = [...text.matchAll(/"src"\s*:\s*"([^"]+)"/g)].map((m) => m[1]);
      if (moduleSrcs.length && !srcM) {
        sourceRoots = [...new Set(moduleSrcs)];
      } else if (moduleSrcs.length) {
        sourceRoots = [...new Set([...sourceRoots, ...moduleSrcs])];
      }
    } catch {
      // defaults
    }
  }
  return { root: path.resolve(root), name, sourceRoots, mainClass, hasProjectFile, hasToml };
}

export function fileBelongsToProject(filePath: string, project: JNativeProjectInfo): boolean {
  const abs = path.resolve(filePath);
  const root = path.resolve(project.root);
  const prefix = root.endsWith(path.sep) ? root : root + path.sep;
  if (!abs.startsWith(prefix) && abs !== root) {
    return false;
  }
  const isSource = abs.endsWith('.java') || abs.endsWith('.jn');
  if (!isSource) {
    return true;
  }
  for (const sr of project.sourceRoots) {
    const srcAbs = path.resolve(root, sr);
    const srcPrefix = srcAbs.endsWith(path.sep) ? srcAbs : srcAbs + path.sep;
    if (abs.startsWith(srcPrefix) || path.dirname(abs) === srcAbs) {
      return true;
    }
  }
  const tests = path.join(root, 'tests');
  const testsPrefix = tests + path.sep;
  if (abs.startsWith(testsPrefix)) {
    return true;
  }
  return false;
}

export function findProjectForFile(filePath: string): JNativeProjectInfo | undefined {
  const abs = path.resolve(filePath);
  let dir = path.dirname(abs);
  for (let i = 0; i < 32; i++) {
    if (isProjectRoot(dir)) {
      const proj = loadProject(dir);
      if (proj && fileBelongsToProject(abs, proj)) {
        return proj;
      }
      return undefined;
    }
    const parent = path.dirname(dir);
    if (parent === dir) {
      break;
    }
    dir = parent;
  }
  return undefined;
}

export function discoverProjectsUnder(roots: string[]): JNativeProjectInfo[] {
  const found: JNativeProjectInfo[] = [];
  const seen = new Set<string>();
  for (const root of roots) {
    collectProjects(root, found, seen, 0);
  }
  return found;
}

function collectProjects(
  dir: string,
  out: JNativeProjectInfo[],
  seen: Set<string>,
  depth: number
): void {
  if (depth > 6 || !fs.existsSync(dir)) {
    return;
  }
  if (isProjectRoot(dir)) {
    const p = loadProject(dir);
    if (p && !seen.has(p.root)) {
      seen.add(p.root);
      out.push(p);
    }
  }
  let entries: fs.Dirent[];
  try {
    entries = fs.readdirSync(dir, { withFileTypes: true });
  } catch {
    return;
  }
  for (const e of entries) {
    if (!e.isDirectory()) {
      continue;
    }
    if (
      e.name === 'node_modules' ||
      e.name === '.git' ||
      e.name === 'build' ||
      e.name === '.jnative' ||
      e.name === 'target'
    ) {
      continue;
    }
    collectProjects(path.join(dir, e.name), out, seen, depth + 1);
  }
}

export function manifestPath(project: JNativeProjectInfo): string {
  if (project.hasProjectFile) {
    return path.join(project.root, 'jnative.project');
  }
  return path.join(project.root, 'jnative.toml');
}

/** Parse JSON build report; ignore non-JSON log lines. */
export function parseBuildReportJson(stdout: string): {
  success: boolean;
  artifact: string | null;
  diagnostics: Array<{ file: string; line: number; column: number; severity: string; message: string }>;
} | undefined {
  const lines = stdout
    .split(/\r?\n/)
    .map((l) => l.trim())
    .filter((l) => l.startsWith('{') && l.includes('"success"'));
  if (!lines.length) {
    return undefined;
  }
  try {
    return JSON.parse(lines[lines.length - 1]);
  } catch {
    return undefined;
  }
}

/** 1-based compiler → 0-based VS Code */
export function toVsCodePosition(line: number, column: number): { line: number; character: number } {
  return {
    line: Math.max(0, (line || 1) - 1),
    character: Math.max(0, (column || 1) - 1),
  };
}
