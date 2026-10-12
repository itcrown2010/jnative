/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

// Author: 张晓达
import * as fs from 'fs';
import * as net from 'net';
import * as path from 'path';
import { ChildProcess, spawn } from 'child_process';
import * as vscode from 'vscode';

let previewProcess: ChildProcess | undefined;
let previewPort: number | undefined;
let previewCwd: string | undefined;

export function isPreviewRunning(): boolean {
  return !!previewProcess && !previewProcess.killed;
}

export function getPreviewInfo(): { port?: number; cwd?: string } {
  return { port: previewPort, cwd: previewCwd };
}

/** Stop only the preview server started by this extension. */
export function stopPreview(): void {
  if (previewProcess && !previewProcess.killed) {
    previewProcess.kill('SIGTERM');
    const p = previewProcess;
    setTimeout(() => {
      if (p && !p.killed) {
        p.kill('SIGKILL');
      }
    }, 2000);
  }
  previewProcess = undefined;
  previewPort = undefined;
  previewCwd = undefined;
}

async function findFreePort(preferred: number): Promise<number> {
  for (let p = preferred; p < preferred + 40; p++) {
    const free = await new Promise<boolean>((resolve) => {
      const s = net.createServer();
      s.once('error', () => resolve(false));
      s.once('listening', () => {
        s.close(() => resolve(true));
      });
      s.listen(p, '127.0.0.1');
    });
    if (free) {
      return p;
    }
  }
  throw new Error(`无法在 ${preferred}–${preferred + 39} 找到空闲端口`);
}

/**
 * Serve a directory with python3 http.server (no shell). Opens Simple Browser when available.
 */
export async function startWebPreview(
  distDir: string,
  channel: vscode.OutputChannel,
  preferredPort = 8765
): Promise<{ url: string; port: number } | undefined> {
  if (!fs.existsSync(distDir) || !fs.existsSync(path.join(distDir, 'index.html'))) {
    vscode.window.showErrorMessage(`预览目录无效或不含 index.html: ${distDir}`);
    return undefined;
  }
  stopPreview();
  let port: number;
  try {
    port = await findFreePort(preferredPort);
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    vscode.window.showErrorMessage(msg);
    return undefined;
  }
  if (port !== preferredPort) {
    channel.appendLine(`[preview] 端口 ${preferredPort} 占用，改用 ${port}`);
  }
  const python = process.platform === 'win32' ? 'python' : 'python3';
  channel.appendLine(`[preview] ${python} -m http.server ${port} --directory ${distDir}`);
  previewProcess = spawn(python, ['-m', 'http.server', String(port), '--directory', distDir], {
    cwd: distDir,
    shell: false,
  });
  previewPort = port;
  previewCwd = distDir;
  previewProcess.stdout?.on('data', (d: Buffer) => channel.append(d.toString('utf8')));
  previewProcess.stderr?.on('data', (d: Buffer) => channel.append(d.toString('utf8')));
  previewProcess.on('close', (code) => {
    channel.appendLine(`[preview-exit] ${code}`);
    if (previewProcess) {
      previewProcess = undefined;
      previewPort = undefined;
      previewCwd = undefined;
    }
  });
  previewProcess.on('error', (err) => {
    channel.appendLine(`[preview-error] ${err.message}`);
    vscode.window.showErrorMessage(
      `无法启动预览服务（需要 ${python}）：${err.message}`
    );
    previewProcess = undefined;
  });
  const url = `http://127.0.0.1:${port}/`;
  try {
    await vscode.commands.executeCommand('simpleBrowser.show', url);
  } catch {
    await vscode.env.openExternal(vscode.Uri.parse(url));
  }
  return { url, port };
}
