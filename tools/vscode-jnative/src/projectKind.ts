/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

// Author: 张晓达
import * as fs from 'fs';
import * as path from 'path';
import { JNativeProjectInfo } from './projectDetect';

export type ProjectKind = 'desktop' | 'web' | 'mobile' | 'unknown';

export function detectProjectKind(project: JNativeProjectInfo): ProjectKind {
  const root = project.root;
  if (fs.existsSync(path.join(root, 'jnative.toml'))) {
    try {
      const toml = fs.readFileSync(path.join(root, 'jnative.toml'), 'utf8');
      if (/\[mobile\]/.test(toml) || /targets\s*=\s*\[/.test(toml)) {
        return 'mobile';
      }
    } catch {
      /* fall through */
    }
  }
  if (
    fs.existsSync(path.join(root, 'android')) ||
    fs.existsSync(path.join(root, 'ios')) ||
    fs.existsSync(path.join(root, 'mobile'))
  ) {
    return 'mobile';
  }
  const projectFile = path.join(root, 'jnative.project');
  if (fs.existsSync(projectFile)) {
    try {
      const text = fs.readFileSync(projectFile, 'utf8');
      if (/wasm32-browser|"kind"\s*:\s*"browser"/.test(text)) {
        return 'web';
      }
      if (/"kind"\s*:\s*"desktop"|triple"\s*:\s*"native"/.test(text)) {
        return 'desktop';
      }
    } catch {
      /* fall through */
    }
  }
  if (fs.existsSync(path.join(root, 'index.html')) && fs.existsSync(path.join(root, 'src', 'App.java'))) {
    return 'web';
  }
  return project.hasProjectFile ? 'desktop' : 'unknown';
}
