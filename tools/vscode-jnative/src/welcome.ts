/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

// Author: 张晓达
import * as vscode from 'vscode';
import { checkToolchainDetailed, ToolchainReport } from './toolchainDetailed';
import { listRecentProjects } from './recentProjects';

const DONT_SHOW_KEY = 'jnative.welcome.dontShowAuto';

export async function maybeShowWelcome(context: vscode.ExtensionContext): Promise<void> {
  if (context.globalState.get<boolean>(DONT_SHOW_KEY)) {
    return;
  }
  const shown = context.globalState.get<boolean>('jnative.welcome.shownOnce');
  if (shown) {
    return;
  }
  await showWelcome(context, true);
  await context.globalState.update('jnative.welcome.shownOnce', true);
}

export async function showWelcome(context: vscode.ExtensionContext, fromAuto = false): Promise<void> {
  const extVer = context.extension.packageJSON.version || '?';
  const report = await checkToolchainDetailed(vscode.workspace.workspaceFolders);
  const panel = vscode.window.createWebviewPanel(
    'jnativeWelcome',
    'JNative 入门',
    vscode.ViewColumn.One,
    {
      enableScripts: true,
      retainContextWhenHidden: true,
      localResourceRoots: [],
    }
  );

  const recent = listRecentProjects();
  panel.webview.html = renderHtml(panel.webview, extVer, report, recent, fromAuto);

  panel.webview.onDidReceiveMessage(async (msg) => {
    switch (msg?.type) {
      case 'command':
        if (typeof msg.command === 'string') {
          await vscode.commands.executeCommand(msg.command);
        }
        break;
      case 'openPath':
        if (typeof msg.path === 'string') {
          await vscode.commands.executeCommand('vscode.openFolder', vscode.Uri.file(msg.path), true);
        }
        break;
      case 'dontShow':
        await context.globalState.update(DONT_SHOW_KEY, true);
        vscode.window.showInformationMessage('已关闭自动显示欢迎页。可用「JNative: 打开入门」再次打开。');
        break;
      case 'redetect': {
        const next = await checkToolchainDetailed(vscode.workspace.workspaceFolders);
        panel.webview.html = renderHtml(panel.webview, extVer, next, listRecentProjects(), fromAuto);
        break;
      }
      case 'openSettings':
        await vscode.commands.executeCommand('workbench.action.openSettings', 'jnative.');
        break;
      default:
        break;
    }
  });
}

function esc(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function statusBadge(status: string): string {
  const map: Record<string, string> = {
    available: '可用',
    missing: '缺失',
    unconfigured: '未配置',
    incompatible: '不兼容',
    unknown: '未知',
  };
  return map[status] || status;
}

function renderHtml(
  webview: vscode.Webview,
  extVer: string,
  report: ToolchainReport,
  recent: string[],
  fromAuto: boolean
): string {
  const csp = `default-src 'none'; style-src ${webview.cspSource} 'unsafe-inline'; script-src 'nonce-jnwelcome';`;
  const rows = report.items
    .map(
      (it) =>
        `<tr><td>${esc(it.name)}</td><td><span class="st ${esc(it.status)}">${esc(
          statusBadge(it.status)
        )}</span></td><td>${esc(it.detail || '')}</td></tr>`
    )
    .join('');
  const recentHtml =
    recent.length === 0
      ? '<p class="muted">暂无最近项目</p>'
      : `<ul class="recent">${recent
          .map(
            (p) =>
              `<li><button type="button" class="link" data-path="${esc(p)}">${esc(p)}</button></li>`
          )
          .join('')}</ul>`;

  return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8"/>
  <meta http-equiv="Content-Security-Policy" content="${csp}"/>
  <meta name="viewport" content="width=device-width, initial-scale=1"/>
  <title>JNative 入门</title>
  <style>
    :root {
      color-scheme: light dark;
      --fg: var(--vscode-foreground);
      --bg: var(--vscode-editor-background);
      --btn-bg: var(--vscode-button-background);
      --btn-fg: var(--vscode-button-foreground);
      --btn-hover: var(--vscode-button-hoverBackground);
      --border: var(--vscode-widget-border, #444);
      --muted: var(--vscode-descriptionForeground);
      --focus: var(--vscode-focusBorder);
    }
    body {
      font-family: var(--vscode-font-family);
      font-size: var(--vscode-font-size);
      color: var(--fg);
      background: var(--bg);
      margin: 0;
      padding: 1.25rem 1.5rem 2.5rem;
      line-height: 1.45;
    }
    h1 { font-size: 1.6rem; margin: 0 0 .25rem; }
    h2 { font-size: 1.1rem; margin: 1.5rem 0 .6rem; }
    .muted { color: var(--muted); }
    .row { display: flex; flex-wrap: wrap; gap: .6rem; margin: .75rem 0; }
    button.action {
      background: var(--btn-bg); color: var(--btn-fg); border: 0;
      padding: .45rem .9rem; cursor: pointer; border-radius: 2px;
    }
    button.action:hover { background: var(--btn-hover); }
    button.action:focus { outline: 1px solid var(--focus); outline-offset: 2px; }
    button.link {
      background: none; border: 0; color: var(--vscode-textLink-foreground);
      cursor: pointer; text-align: left; padding: .2rem 0; font: inherit;
      text-decoration: underline;
    }
    button.link:focus { outline: 1px solid var(--focus); }
    table { border-collapse: collapse; width: 100%; max-width: 52rem; }
    th, td { border-bottom: 1px solid var(--border); padding: .4rem .5rem; text-align: left; vertical-align: top; }
    .st.available { color: var(--vscode-testing-iconPassed, #3ba55d); }
    .st.missing, .st.unconfigured, .st.incompatible { color: var(--vscode-errorForeground); }
    .recent { list-style: none; padding: 0; margin: 0; }
    .recent li { margin: .25rem 0; }
    .banner { margin-bottom: 1rem; padding: .6rem .8rem; border: 1px solid var(--border); }
  </style>
</head>
<body>
  <header>
    <h1>JNative 入门</h1>
    <p class="muted">插件 ${esc(extVer)} · SDK ${esc(report.sdkVersion || '未检测')} · 统一桌面 / Web / 移动入口</p>
    ${fromAuto ? '<p class="banner muted">首次打开。可在下方选择不再自动显示。</p>' : ''}
  </header>

  <h2>工具链状态</h2>
  <table>
    <thead><tr><th scope="col">组件</th><th scope="col">状态</th><th scope="col">说明</th></tr></thead>
    <tbody>${rows}</tbody>
  </table>
  <div class="row">
    <button type="button" class="action" data-cmd="redetect">重新检测</button>
    <button type="button" class="action" data-cmd="openSettings">打开设置</button>
    <button type="button" class="action" data-cmd="jnative.checkToolchain">完整检查（输出面板）</button>
  </div>
  <p class="muted">不会静默下载软件、修改系统 PATH、接受许可或配置签名。请按官方说明手动安装缺失工具。</p>

  <h2>创建项目</h2>
  <p class="muted">同一业务主题：整数加法器（计算始终在 JNative 中执行）。</p>
  <div class="row">
    <button type="button" class="action" data-cmd="jnative.createDesktopProject">创建桌面项目</button>
    <button type="button" class="action" data-cmd="jnative.createWebProject">创建 Web 项目</button>
    <button type="button" class="action" data-cmd="jnative.createMobileProject">创建移动项目</button>
    <button type="button" class="action" data-cmd="jnative.createProject">创建向导…</button>
  </div>

  <h2>教程与示例</h2>
  <div class="row">
    <button type="button" class="action" data-cmd="jnative.openTutorial">打开教程</button>
    <button type="button" class="action" data-cmd="jnative.openExamples">打开示例说明</button>
  </div>

  <h2>最近的 JNative 项目</h2>
  ${recentHtml}

  <h2>其他</h2>
  <div class="row">
    <button type="button" class="action" data-cmd="dontShow">不再自动显示</button>
  </div>

  <script nonce="jnwelcome">
    const vscode = acquireVsCodeApi();
    document.querySelectorAll('button[data-cmd]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const cmd = btn.getAttribute('data-cmd');
        if (cmd === 'dontShow' || cmd === 'redetect' || cmd === 'openSettings') {
          vscode.postMessage({ type: cmd });
        } else {
          vscode.postMessage({ type: 'command', command: cmd });
        }
      });
    });
    document.querySelectorAll('button[data-path]').forEach((btn) => {
      btn.addEventListener('click', () => {
        vscode.postMessage({ type: 'openPath', path: btn.getAttribute('data-path') });
      });
    });
  </script>
</body>
</html>`;
}
