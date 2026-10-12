/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

// Author: 张晓达
import * as fs from 'fs';
import * as vscode from 'vscode';
import { isProjectRoot } from './projectDetect';

const KEY = 'jnative.recentProjects';
const MAX = 8;

let store: vscode.Memento | undefined;

export function initRecentProjects(context: vscode.ExtensionContext): void {
  store = context.globalState;
}

export function rememberProject(root: string): void {
  if (!store) {
    return;
  }
  const abs = root;
  const prev = store.get<string[]>(KEY, []);
  const next = [abs, ...prev.filter((p) => p !== abs)].slice(0, MAX);
  void store.update(KEY, next);
}

export function listRecentProjects(): string[] {
  if (!store) {
    return [];
  }
  return (store.get<string[]>(KEY, []) || []).filter((p) => {
    try {
      return fs.existsSync(p) && isProjectRoot(p);
    } catch {
      return false;
    }
  });
}
