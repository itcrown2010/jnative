/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

// Author: 张晓达
import * as fs from 'fs';
import * as path from 'path';
import { JNativeProject } from './project';

/** CodeLLDB (vadimcn.vscode-lldb) launch fields used by this extension. */
export interface JNativeLldbLaunch {
  type: 'lldb';
  request: 'launch';
  name: string;
  program: string;
  cwd: string;
  args: string[];
  env?: Record<string, string>;
  sourceLanguages: string[];
  preLaunchTask?: string;
  /** LLDB startup commands (e.g. import JNative formatters). */
  initCommands?: string[];
}

/**
 * Resolve tools/lldb/jnative_formatters.py from a project root by walking parents.
 */
export function resolveFormatterScript(projectRoot: string): string | undefined {
  let dir = path.resolve(projectRoot);
  for (let i = 0; i < 8; i++) {
    const candidate = path.join(dir, 'tools', 'lldb', 'jnative_formatters.py');
    if (fs.existsSync(candidate)) {
      return candidate;
    }
    const parent = path.dirname(dir);
    if (parent === dir) {
      break;
    }
    dir = parent;
  }
  return undefined;
}

export function buildLaunchConfig(
  project: JNativeProject,
  artifact: string,
  runArgs: string[],
  env?: Record<string, string>
): JNativeLldbLaunch {
  const cfg: JNativeLldbLaunch = {
    type: 'lldb',
    request: 'launch',
    name: `JNative: ${project.name}`,
    program: artifact,
    cwd: project.root,
    args: runArgs,
    sourceLanguages: ['jnative', 'cpp', 'c'],
  };
  if (env && Object.keys(env).length > 0) {
    cfg.env = env;
  }
  const fmt = resolveFormatterScript(project.root);
  if (fmt) {
    cfg.initCommands = [`command script import '${fmt.replace(/'/g, "\\'")}'`];
  }
  return cfg;
}

/** True when a build failure must not reuse a previous artifact path. */
export function shouldRefuseStaleArtifact(
  buildSuccess: boolean,
  artifact: string | undefined,
  exists: (p: string) => boolean
): boolean {
  if (!buildSuccess || !artifact) {
    return true;
  }
  return !exists(path.resolve(artifact));
}
