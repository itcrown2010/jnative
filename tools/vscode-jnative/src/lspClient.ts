/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

// Author: 张晓达
import * as vscode from 'vscode';
import { findProjectForFile } from './project';
import { buildArgv, resolveToolchain } from './toolchain';

type LspNode = typeof import('vscode-languageclient/node');
type LanguageClient = import('vscode-languageclient/node').LanguageClient;

let client: LanguageClient | undefined;
let clientDisposable: vscode.Disposable | undefined;
let lspApi: LspNode | undefined;
let statusItem: vscode.StatusBarItem | undefined;
let lspLog: vscode.OutputChannel | undefined;
let startGeneration = 0;
let restartTimestamps: number[] = [];
let crashRestarts = 0;
let intentionalStop = false;
const MAX_AUTO_RESTARTS = 3;
const RESTART_WINDOW_MS = 60_000;

function loadLspApi(): LspNode | undefined {
  if (lspApi) {
    return lspApi;
  }
  try {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    lspApi = require('vscode-languageclient/node') as LspNode;
    return lspApi;
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    void vscode.window.showWarningMessage(
      `JNative LSP 不可用（缺少 vscode-languageclient）: ${msg}。创建/构建/运行仍可使用。`
    );
    setStatus('failed', '缺少 vscode-languageclient');
    return undefined;
  }
}

export function getLspLog(): vscode.OutputChannel {
  if (!lspLog) {
    lspLog = vscode.window.createOutputChannel('JNative Language Server');
  }
  return lspLog;
}

function ensureStatus(context: vscode.ExtensionContext): vscode.StatusBarItem {
  if (!statusItem) {
    statusItem = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left, 50);
    statusItem.command = 'jnative.showLanguageServerLog';
    context.subscriptions.push(statusItem);
    statusItem.show();
  }
  return statusItem;
}

export function setStatus(state: 'starting' | 'ready' | 'failed' | 'off', detail?: string): void {
  if (!statusItem) {
    return;
  }
  switch (state) {
    case 'starting':
      statusItem.text = '$(sync~spin) JNative LS';
      statusItem.tooltip = detail || '语言服务启动中…';
      statusItem.backgroundColor = undefined;
      break;
    case 'ready':
      statusItem.text = '$(check) JNative LS';
      statusItem.tooltip = detail || '语言服务就绪（补全 / 诊断 / 导航）';
      statusItem.backgroundColor = undefined;
      break;
    case 'failed':
      statusItem.text = '$(error) JNative LS';
      statusItem.tooltip = detail || '语言服务失败 — 点击查看日志';
      statusItem.backgroundColor = new vscode.ThemeColor('statusBarItem.errorBackground');
      break;
    case 'off':
      statusItem.text = '$(circle-slash) JNative LS';
      statusItem.tooltip = detail || '语言服务未启用';
      statusItem.backgroundColor = undefined;
      break;
  }
}

function redact(line: string): string {
  return line
    .replace(/(Authorization:\s*)\S+/gi, '$1***')
    .replace(/(api[_-]?key\s*[=:]\s*)\S+/gi, '$1***')
    .replace(/(password\s*[=:]\s*)\S+/gi, '$1***')
    .replace(/(token\s*[=:]\s*)\S+/gi, '$1***');
}

function allowRestart(manual: boolean): boolean {
  const now = Date.now();
  restartTimestamps = restartTimestamps.filter((t) => now - t < RESTART_WINDOW_MS);
  if (manual) {
    restartTimestamps.push(now);
    crashRestarts = 0;
    return true;
  }
  if (crashRestarts >= MAX_AUTO_RESTARTS) {
    return false;
  }
  if (restartTimestamps.length >= MAX_AUTO_RESTARTS) {
    return false;
  }
  restartTimestamps.push(now);
  crashRestarts++;
  return true;
}

async function checkJarCompat(
  resolved: { invocation: { source: string } },
  log: vscode.OutputChannel
): Promise<void> {
  log.appendLine(`[lsp] toolchain source=${resolved.invocation.source}`);
  // Prefer jar from settings; warn if missing after resolve said OK (race / deleted).
  const jar = vscode.workspace.getConfiguration('jnative').get<string>('jarPath') || '';
  if (jar && !(await import('fs')).existsSync(jar)) {
    log.appendLine(`[lsp] WARN jnative.jarPath missing on disk: ${jar}`);
    setStatus('failed', 'jnative.jarPath 文件不存在');
  }
}

export async function startLanguageServer(
  context: vscode.ExtensionContext,
  opts?: { manual?: boolean; reason?: string }
): Promise<void> {
  const manual = !!opts?.manual;
  const gen = ++startGeneration;
  ensureStatus(context);
  intentionalStop = true;
  await stopLanguageServer();
  intentionalStop = false;
  if (gen !== startGeneration) {
    return;
  }
  if (!vscode.workspace.isTrusted) {
    setStatus('off', '工作区未受信任 — 语言服务未启动');
    getLspLog().appendLine('[lsp] skipped: workspace untrusted');
    return;
  }
  const cfg = vscode.workspace.getConfiguration('jnative');
  if (!cfg.get<boolean>('enableLanguageServer', true)) {
    setStatus('off', 'jnative.enableLanguageServer=false');
    return;
  }
  const api = loadLspApi();
  if (!api) {
    return;
  }
  setStatus('starting', opts?.reason || '启动中…');
  const log = getLspLog();
  // Cap log channel growth: clear when very large (VS Code channel has no size API — soft note)
  log.appendLine(`[lsp] resolving toolchain… (${opts?.reason || 'start'})`);
  const resolved = await resolveToolchain(vscode.workspace.workspaceFolders);
  if (!resolved.invocation) {
    const reason = resolved.missing || '未找到 JNative 工具链';
    setStatus('failed', reason);
    log.appendLine(`[lsp] start failed: ${reason}`);
    const pick = await vscode.window.showErrorMessage(
      `JNative 语言服务未启动：${reason}`,
      '打开设置',
      '查看日志'
    );
    if (pick === '打开设置') {
      await vscode.commands.executeCommand('workbench.action.openSettings', 'jnative.');
    } else if (pick === '查看日志') {
      log.show(true);
    }
    return;
  }
  await checkJarCompat({ invocation: resolved.invocation }, log);
  const argv = buildArgv(resolved.invocation, ['lsp']);
  log.appendLine(`[lsp] start: ${argv[0]} ${argv.slice(1).join(' ')}`.trim());
  log.appendLine(`[lsp] source: ${resolved.invocation.source}`);
  const serverOptions: import('vscode-languageclient/node').ServerOptions = {
    command: argv[0],
    args: argv.slice(1),
    options: { shell: false },
  };
  const clientOptions: import('vscode-languageclient/node').LanguageClientOptions = {
    documentSelector: [
      { language: 'jnative', scheme: 'file' },
      { pattern: '**/*.{jn,java}', scheme: 'file' },
    ],
    synchronize: {
      configurationSection: 'jnative',
      fileEvents: [
        vscode.workspace.createFileSystemWatcher('**/*.{java,jn}'),
        vscode.workspace.createFileSystemWatcher('**/jnative.project'),
        vscode.workspace.createFileSystemWatcher('**/jnative.toml'),
        vscode.workspace.createFileSystemWatcher('**/jnative.lock'),
      ],
    },
    initializationOptions: {
      formatIndentSize: cfg.get<number>('formatIndentSize', 4),
      formatInsertSpaces: cfg.get<boolean>('formatInsertSpaces', true),
    },
    outputChannel: log,
    traceOutputChannel: log,
    middleware: {
      didOpen: async (doc, next) => {
        // Do not attach LSP to ordinary Java files outside JNative projects
        if (doc.languageId === 'jnative' || doc.fileName.endsWith('.jn')) {
          return next(doc);
        }
        if (doc.fileName.endsWith('.java') && findProjectForFile(doc.fileName)) {
          return next(doc);
        }
        return;
      },
    },
  };
  client = new api.LanguageClient('jnative', 'JNative Language Server', serverOptions, clientOptions);
  // Single disposable — avoid stacking clients on context.subscriptions across restarts
  clientDisposable?.dispose();
  clientDisposable = client;
  client.onDidChangeState((e) => {
    if (e.newState === api.State.Running) {
      setStatus('ready', `就绪 (${resolved.invocation!.source})`);
      log.appendLine('[lsp] state=Running');
      crashRestarts = 0;
    } else if (e.newState === api.State.Starting) {
      setStatus('starting');
    } else if (e.newState === api.State.Stopped) {
      if (intentionalStop) {
        log.appendLine('[lsp] state=Stopped (intentional)');
        return;
      }
      setStatus('failed', '语言服务意外停止');
      log.appendLine('[lsp] state=Stopped (unexpected)');
      if (gen === startGeneration && allowRestart(false)) {
        log.appendLine('[lsp] auto-restart after unexpected stop (rate-limited)');
        void startLanguageServer(context, { reason: 'auto-restart after stop' });
      } else if (gen === startGeneration) {
        void vscode.window
          .showErrorMessage(
            'JNative 语言服务反复退出，已停止自动重启。请查看日志或手动「重启语言服务」。',
            '查看日志',
            '手动重启'
          )
          .then((pick) => {
            if (pick === '查看日志') {
              log.show(true);
            } else if (pick === '手动重启') {
              void restartLanguageServer(context);
            }
          });
      }
    }
  });
  try {
    await client.start();
    if (gen !== startGeneration) {
      await stopLanguageServer();
      return;
    }
    setStatus('ready', `就绪 (${resolved.invocation.source})`);
    log.appendLine('[lsp] started OK');
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    setStatus('failed', msg);
    log.appendLine(`[lsp] start error: ${redact(msg)}`);
    const pick = await vscode.window.showErrorMessage(
      `JNative LSP 启动失败: ${msg}`,
      '查看日志',
      '重试'
    );
    if (pick === '查看日志') {
      log.show(true);
    } else if (pick === '重试' && allowRestart(true)) {
      await startLanguageServer(context, { manual: true, reason: 'user retry' });
    }
    client = undefined;
    clientDisposable = undefined;
  }
}

export async function stopLanguageServer(): Promise<void> {
  intentionalStop = true;
  if (client) {
    try {
      const api = lspApi;
      if (!api || client.state !== api.State.Stopped) {
        await client.stop();
      }
    } catch {
      // ignore
    }
    try {
      clientDisposable?.dispose();
    } catch {
      // ignore
    }
    client = undefined;
    clientDisposable = undefined;
  }
}

export async function restartLanguageServer(context: vscode.ExtensionContext): Promise<void> {
  if (!allowRestart(true)) {
    void vscode.window.showWarningMessage(
      '重启过于频繁，请稍后再试（防止崩溃循环）。'
    );
    return;
  }
  getLspLog().appendLine('[lsp] manual restart requested');
  await startLanguageServer(context, { manual: true, reason: 'manual restart' });
}

export function isLspRunning(): boolean {
  const api = lspApi;
  return !!client && !!api && client.state === api.State.Running;
}

export function disposeLspUi(): void {
  statusItem?.dispose();
  statusItem = undefined;
  lspLog?.dispose();
  lspLog = undefined;
}
