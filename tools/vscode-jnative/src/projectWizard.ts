/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

// Author: 张晓达
import * as fs from 'fs';
import * as path from 'path';
import * as vscode from 'vscode';
import { getOutput } from './output';
import { rememberProject } from './recentProjects';
import { runCapture, resolveToolchain, promptMissingToolchain, ToolchainInvocation } from './toolchain';

export type WizardKind = 'desktop' | 'web' | 'mobile';

function templatesRoot(extensionPath: string): string {
  return path.join(extensionPath, 'templates');
}

function replaceAll(text: string, vars: Record<string, string>): string {
  let out = text;
  for (const [k, v] of Object.entries(vars)) {
    out = out.split(`{{${k}}}`).join(v);
  }
  return out;
}

function copyTemplateDir(srcDir: string, destDir: string, vars: Record<string, string>): void {
  fs.mkdirSync(destDir, { recursive: true });
  for (const ent of fs.readdirSync(srcDir, { withFileTypes: true })) {
    const from = path.join(srcDir, ent.name);
    const to = path.join(destDir, ent.name);
    if (ent.isDirectory()) {
      copyTemplateDir(from, to, vars);
    } else {
      const raw = fs.readFileSync(from, 'utf8');
      fs.writeFileSync(to, replaceAll(raw, vars), 'utf8');
    }
  }
}

function pathExistsConflict(target: string, kind: WizardKind): string | undefined {
  if (!fs.existsSync(target)) {
    return undefined;
  }
  const markers =
    kind === 'mobile'
      ? ['jnative.toml', 'src/App.java', 'src/App.jn']
      : kind === 'web'
        ? ['jnative.project', 'src/App.java', 'index.html']
        : ['jnative.project', 'src/Main.java', 'src/Main.jn'];
  for (const m of markers) {
    if (fs.existsSync(path.join(target, m))) {
      return m;
    }
  }
  // Non-empty directory with any of our files
  try {
    if (fs.readdirSync(target).length > 0 && fs.existsSync(path.join(target, 'src'))) {
      return 'src/';
    }
  } catch {
    /* ignore */
  }
  return undefined;
}

async function requireInv(): Promise<ToolchainInvocation | undefined> {
  if (!vscode.workspace.isTrusted) {
    vscode.window.showWarningMessage('工作区未受信任：禁止创建项目。');
    return undefined;
  }
  const resolved = await resolveToolchain(vscode.workspace.workspaceFolders);
  if (!resolved.invocation) {
    await promptMissingToolchain(resolved.missing || '工具链未配置');
    return undefined;
  }
  return resolved.invocation;
}

async function pickParentAndName(defaultName: string): Promise<{ parent: string; name: string; target: string } | undefined> {
  const folderUri = await vscode.window.showOpenDialog({
    canSelectFiles: false,
    canSelectFolders: true,
    canSelectMany: false,
    openLabel: '选择父目录',
  });
  if (!folderUri?.length) {
    return undefined;
  }
  const parent = folderUri[0].fsPath;
  const name = await vscode.window.showInputBox({
    prompt: '项目名称',
    value: defaultName,
    validateInput: (v) => (/^[A-Za-z_][A-Za-z0-9_-]*$/.test(v) ? undefined : '非法项目名（字母/数字/_/-）'),
  });
  if (!name) {
    return undefined;
  }
  return { parent, name, target: path.join(parent, name) };
}

export async function createFromWizard(
  extensionPath: string,
  kind?: WizardKind
): Promise<string | undefined> {
  const inv = await requireInv();
  if (!inv) {
    return undefined;
  }

  let selected = kind;
  if (!selected) {
    const pick = await vscode.window.showQuickPick(
      [
        {
          label: '桌面应用',
          description: 'native 窗口 + 输入/按钮',
          detail: '需要 clang/LLVM；调试可用 CodeLLDB',
          projectKind: 'desktop' as WizardKind,
        },
        {
          label: 'Web 应用',
          description: 'WASM + 页面宿主',
          detail: '需要 wat2wasm；运行启动本地预览',
          projectKind: 'web' as WizardKind,
        },
        {
          label: '移动应用',
          description: 'WASM 核心 + Android/iOS WebView 容器',
          detail: '非完整原生移动运行时；需对应 SDK/Xcode',
          projectKind: 'mobile' as WizardKind,
        },
      ],
      { title: 'JNative：选择项目类型', placeHolder: '同一业务主题：整数加法器' }
    );
    if (!pick) {
      return undefined;
    }
    selected = pick.projectKind;
  }

  if (!selected) {
    return undefined;
  }
  const defaults: Record<WizardKind, string> = {
    desktop: 'desktop-adder',
    web: 'web-adder',
    mobile: 'my-mobile-app',
  };
  const loc = await pickParentAndName(defaults[selected]);
  if (!loc) {
    return undefined;
  }
  const conflict = pathExistsConflict(loc.target, selected);
  if (conflict) {
    vscode.window.showErrorMessage(`拒绝覆盖已有文件: ${path.join(loc.target, conflict)}`);
    return undefined;
  }

  const channel = getOutput();
  channel.show(true);
  channel.appendLine(`[create] kind=${selected} → ${loc.target}`);

  if (selected === 'mobile') {
    const result = await runCapture(inv, ['new', loc.name, '--template', 'mobile', '--dir', loc.parent], {
      cwd: loc.parent,
      timeoutMs: 120000,
    });
    channel.appendLine(result.stdout + result.stderr);
    if (result.code !== 0) {
      vscode.window.showErrorMessage('创建移动项目失败，见 JNative 输出。请先运行「检查工具链」。');
      return undefined;
    }
  } else {
    const tpl = path.join(templatesRoot(extensionPath), selected === 'web' ? 'web-adder' : 'desktop-adder');
    if (!fs.existsSync(tpl)) {
      vscode.window.showErrorMessage(`扩展内模板缺失: ${tpl}`);
      return undefined;
    }
    try {
      copyTemplateDir(tpl, loc.target, { APP_NAME: loc.name });
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      vscode.window.showErrorMessage(`写入模板失败: ${msg}`);
      return undefined;
    }
  }

  rememberProject(loc.target);
  const actions = ['打开项目', '打开 README'];
  if (selected === 'web' || selected === 'desktop') {
    actions.push('运行');
  }
  const open = await vscode.window.showInformationMessage(
    `已创建 ${selected} 项目 ${loc.target}`,
    ...actions
  );
  if (open === '打开项目') {
    await vscode.commands.executeCommand('vscode.openFolder', vscode.Uri.file(loc.target), true);
  } else if (open === '打开 README') {
    const readme = path.join(loc.target, 'README.md');
    if (fs.existsSync(readme)) {
      await vscode.window.showTextDocument(vscode.Uri.file(readme));
    }
  } else if (open === '运行') {
    await vscode.commands.executeCommand('vscode.openFolder', vscode.Uri.file(loc.target), true);
    // Run after folder open is best-effort; user can use Run command in new window.
    vscode.window.showInformationMessage('请在新窗口中执行「JNative: 运行」。');
  }
  return loc.target;
}
