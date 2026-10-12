/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

// Author: 张晓达
import * as fs from 'fs';
import * as path from 'path';
import * as vscode from 'vscode';
import { findOnPath, resolveToolchain, runCapture } from './toolchain';

export type ToolStatus = 'available' | 'missing' | 'unconfigured' | 'incompatible' | 'unknown';

export interface ToolchainItem {
  name: string;
  status: ToolStatus;
  detail?: string;
}

export interface ToolchainReport {
  sdkVersion?: string;
  items: ToolchainItem[];
  okDesktop: boolean;
  okWeb: boolean;
  okAndroid: boolean;
  okIos: boolean;
}

async function binStatus(name: string, args: string[] = ['--version']): Promise<ToolchainItem> {
  const p = await findOnPath(name);
  if (!p) {
    return { name, status: 'missing', detail: `PATH 中未找到 ${name}` };
  }
  const r = await runCapture({ command: p, prefixArgs: [], source: name }, args, { timeoutMs: 12000 });
  const out = (r.stdout || r.stderr).trim().split('\n')[0] || p;
  if (r.code === 0 || out.length > 0) {
    return { name, status: 'available', detail: out.slice(0, 120) };
  }
  return { name, status: 'incompatible', detail: `exit ${r.code}` };
}

export async function checkToolchainDetailed(
  folders: readonly vscode.WorkspaceFolder[] | undefined
): Promise<ToolchainReport> {
  const items: ToolchainItem[] = [];
  const resolved = await resolveToolchain(folders);
  let sdkVersion: string | undefined;

  if (!resolved.invocation) {
    items.push({
      name: 'JNative SDK',
      status: 'unconfigured',
      detail: resolved.missing || '请设置 jnative.executablePath 或 jnative.jarPath',
    });
  } else {
    const inv = resolved.invocation;
    const ver = await runCapture(inv, ['--version'], { timeoutMs: 20000 });
    const text = (ver.stdout || ver.stderr).trim();
    if (ver.code === 0 && /jnative/i.test(text)) {
      sdkVersion = text.split('\n')[0];
      items.push({
        name: 'JNative SDK',
        status: 'available',
        detail: `${sdkVersion} (${inv.source})`,
      });
    } else {
      items.push({
        name: 'JNative SDK',
        status: 'incompatible',
        detail: text || `exit ${ver.code}`,
      });
    }

    const doctor = await runCapture(inv, ['doctor'], { timeoutMs: 60000 });
    const dtext = doctor.stdout || doctor.stderr;
    items.push({
      name: '原生工具链 (doctor)',
      status: doctor.code === 0 ? 'available' : 'missing',
      detail: dtext
        .split('\n')
        .filter((l) => /FAIL|OK|clang|LLVM|Linker/i.test(l))
        .slice(0, 4)
        .join(' · ')
        .slice(0, 160) || `exit ${doctor.code}`,
    });

    const mobile = await runCapture(inv, ['doctor', '--mobile'], { timeoutMs: 90000 });
    const mtext = mobile.stdout || mobile.stderr;
    const webOk = /wat2wasm|wasm|WEB|web/i.test(mtext) && !/wat2wasm.*(missing|not found|FAIL)/i.test(mtext);
    items.push({
      name: 'WASM / Web (wat2wasm 等)',
      status: webOk || mobile.code === 0 ? 'available' : 'missing',
      detail: mtext
        .split('\n')
        .filter((l) => /wat2wasm|web|wasm/i.test(l))
        .slice(0, 3)
        .join(' · ')
        .slice(0, 160) || `mobile doctor exit ${mobile.code}`,
    });

    const androidOk = /android|sdk|adb/i.test(mtext) && !/Android SDK.*(missing|not found|FAIL)/i.test(mtext);
    items.push({
      name: 'Android SDK / 构建工具',
      status: /FAIL.*[Aa]ndroid|[Aa]ndroid.*(missing|not found)/.test(mtext)
        ? 'missing'
        : androidOk
          ? 'available'
          : 'unknown',
      detail: mtext
        .split('\n')
        .filter((l) => /[Aa]ndroid|adb|gradle/i.test(l))
        .slice(0, 3)
        .join(' · ')
        .slice(0, 160),
    });

    if (process.platform === 'darwin') {
      const xcode = await runCapture(
        { command: 'xcodebuild', prefixArgs: [], source: 'xcode' },
        ['-version'],
        { timeoutMs: 15000 }
      );
      items.push({
        name: 'Xcode / iOS',
        status: xcode.code === 0 ? 'available' : 'missing',
        detail: (xcode.stdout || xcode.stderr).trim().split('\n')[0] || '未安装 Xcode 命令行工具',
      });
    } else {
      items.push({
        name: 'Xcode / iOS',
        status: 'missing',
        detail: '仅在 macOS 上可用',
      });
    }
  }

  items.push(await binStatus('clang'));
  items.push(await binStatus('python3', ['--version']));

  // CodeLLDB extension
  const lldb = vscode.extensions.getExtension('vadimcn.vscode-lldb');
  items.push({
    name: '调试适配器 CodeLLDB',
    status: lldb ? 'available' : 'missing',
    detail: lldb ? `已安装 ${lldb.packageJSON.version || ''}` : '原生调试需要扩展 vadimcn.vscode-lldb',
  });

  // Install docs hint (bundled)
  const extRoot = vscode.extensions.getExtension('jnative.jnative')?.extensionPath;
  if (extRoot && fs.existsSync(path.join(extRoot, 'README.md'))) {
    items.push({
      name: '安装说明',
      status: 'available',
      detail: '见扩展 README / 仓库 docs/tutorial（无虚构下载地址）',
    });
  }

  const byName = (n: string) => items.find((i) => i.name.includes(n));
  const sdkOk = byName('JNative SDK')?.status === 'available';
  const nativeOk = byName('原生')?.status === 'available' && byName('clang')?.status === 'available';
  const webItem = byName('WASM');
  const androidItem = byName('Android');
  const iosItem = byName('Xcode');

  return {
    sdkVersion,
    items,
    okDesktop: !!(sdkOk && nativeOk),
    okWeb: !!(sdkOk && webItem && webItem.status !== 'missing'),
    okAndroid: !!(sdkOk && androidItem && androidItem.status === 'available'),
    okIos: !!(sdkOk && iosItem && iosItem.status === 'available'),
  };
}
