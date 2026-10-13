/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

// Author: 张晓达
import * as fs from 'fs';
import * as path from 'path';
import * as vscode from 'vscode';
import { JNativeProject, pickProject } from './project';
import { buildProject, getOutput } from './runner';
import { buildLaunchConfig, JNativeLldbLaunch } from './launchConfig';
import { detectProjectKind } from './projectKind';
import { getConfiguredTarget } from './targetContext';

export type { JNativeLldbLaunch };
export { buildLaunchConfig };

export function isCodeLldbInstalled(): boolean {
  return !!vscode.extensions.getExtension('vadimcn.vscode-lldb');
}

export async function ensureCodeLldb(): Promise<boolean> {
  const ext = vscode.extensions.getExtension('vadimcn.vscode-lldb');
  if (!ext) {
    const choice = await vscode.window.showErrorMessage(
      '未安装 CodeLLDB（vadimcn.vscode-lldb）。源码调试需要该适配器；扩展不会自动安装。',
      '打开扩展市场'
    );
    if (choice === '打开扩展市场') {
      await vscode.commands.executeCommand('workbench.extensions.search', 'vadimcn.vscode-lldb');
    }
    return false;
  }
  if (!ext.isActive) {
    await ext.activate();
  }
  return true;
}

/**
 * Provides dynamic launch configs that rebuild with --debug before startDebugging.
 * Does not write or overwrite workspace launch.json.
 */
export class JNativeDebugConfigurationProvider
  implements vscode.DebugConfigurationProvider
{
  async provideDebugConfigurations(
    folder: vscode.WorkspaceFolder | undefined
  ): Promise<vscode.DebugConfiguration[]> {
    if (!folder) {
      return [];
    }
    const project = await pickProject([folder], undefined);
    if (!project) {
      return [];
    }
    return [
      {
        type: 'lldb',
        request: 'launch',
        name: `JNative: ${project.name}`,
        program: '${command:jnative.resolveDebugProgram}',
        cwd: project.root,
        args: [],
        sourceLanguages: ['jnative', 'cpp', 'c'],
      },
    ];
  }

  async resolveDebugConfiguration(
    folder: vscode.WorkspaceFolder | undefined,
    config: vscode.DebugConfiguration
  ): Promise<vscode.DebugConfiguration | undefined> {
    if (!vscode.workspace.isTrusted) {
      vscode.window.showWarningMessage('工作区未受信任：禁止调试。');
      return undefined;
    }
    // Empty config from F5 with no launch.json — synthesize JNative debug
    if (!config.type && !config.request && !config.name) {
      const editor = vscode.window.activeTextEditor?.document.uri.fsPath;
      const project = await pickProject(vscode.workspace.workspaceFolders, editor);
      if (!project) {
        return undefined;
      }
      if (!(await refuseNonNativeDebug(project))) {
        return undefined;
      }
      if (!(await ensureCodeLldb())) {
        return undefined;
      }
      const built = await buildProject(project, true);
      if (!built.success || !built.artifact) {
        vscode.window.showErrorMessage('调试构建失败，未启动调试器（不会使用旧产物）。');
        return undefined;
      }
      const runArgs = vscode.workspace.getConfiguration('jnative').get<string[]>('runArguments') ?? [];
      const env = vscode.workspace.getConfiguration('jnative').get<Record<string, string>>('debugEnv') ?? {};
      return buildLaunchConfig(project, built.artifact, runArgs, env) as vscode.DebugConfiguration;
    }

    if (config.type === 'lldb' && typeof config.program === 'string') {
      if (config.program.includes('jnative.resolveDebugProgram') || config.program === '') {
        const editor = vscode.window.activeTextEditor?.document.uri.fsPath;
        const project = await pickProject(
          folder ? [folder] : vscode.workspace.workspaceFolders,
          editor
        );
        if (!project) {
          return undefined;
        }
        if (!(await refuseNonNativeDebug(project))) {
          return undefined;
        }
        if (!(await ensureCodeLldb())) {
          return undefined;
        }
        const built = await buildProject(project, true);
        if (!built.success || !built.artifact) {
          vscode.window.showErrorMessage('调试构建失败，未启动调试器（不会使用旧产物）。');
          return undefined;
        }
        config.program = built.artifact;
        config.cwd = config.cwd || project.root;
        config.sourceLanguages = config.sourceLanguages ?? ['jnative', 'cpp', 'c'];
      }
    }
    return config;
  }
}

/**
 * CodeLLDB 仅覆盖原生桌面可执行文件。
 * 不把「仅映射 JS 胶水的 source map」或「无 DWARF 的 WASM」伪装成 JNative 源码调试。
 */
async function refuseNonNativeDebug(project: JNativeProject): Promise<boolean> {
  const kind = detectProjectKind(project);
  const folder = vscode.workspace.getWorkspaceFolder(vscode.Uri.file(project.root));
  const target = getConfiguredTarget(folder);
  if (kind === 'web' || kind === 'mobile' || target === 'wasm32-browser') {
    const channel = getOutput();
    channel.show(true);
    channel.appendLine('[debug] WASM / 移动容器不支持 CodeLLDB 源码调试。');
    channel.appendLine('[debug] 请使用分层日志与浏览器/WebView 检查（见 docs/developer/WASM_SOURCE_DEBUG.md）。');
    channel.appendLine('[debug] 缺口：浏览器侧无可靠 .jn DWARF/源映射；原生 LLDB 不会自动覆盖 WASM。');
    vscode.window.showWarningMessage(
      'Web/移动目标无 JNative 源码级调试（不启动无效调试会话）。请用输出面板分层日志与 WebView 检查。'
    );
    return false;
  }
  return true;
}

/** Write a sample launch.json only when missing (never overwrite). */
export async function generateLaunchJson(): Promise<void> {
  if (!vscode.workspace.isTrusted) {
    vscode.window.showWarningMessage('工作区未受信任：禁止写入调试配置。');
    return;
  }
  const editor = vscode.window.activeTextEditor?.document.uri.fsPath;
  const project = await pickProject(vscode.workspace.workspaceFolders, editor);
  if (!project) {
    return;
  }
  const vscodeDir = path.join(project.root, '.vscode');
  const launchPath = path.join(vscodeDir, 'launch.json');
  if (fs.existsSync(launchPath)) {
    vscode.window.showInformationMessage(`已存在 ${launchPath}，未覆盖。`);
    const doc = await vscode.workspace.openTextDocument(launchPath);
    await vscode.window.showTextDocument(doc);
    return;
  }
  fs.mkdirSync(vscodeDir, { recursive: true });
  const content = {
    version: '0.2.0',
    configurations: [
      {
        type: 'lldb',
        request: 'launch',
        name: `JNative: ${project.name}`,
        program: '${command:jnative.resolveDebugProgram}',
        cwd: '${workspaceFolder}',
        args: [],
        sourceLanguages: ['jnative', 'cpp', 'c'],
      },
    ],
  };
  fs.writeFileSync(launchPath, JSON.stringify(content, null, 2) + '\n', 'utf8');
  getOutput().appendLine(`[debug] wrote ${launchPath}`);
  const doc = await vscode.workspace.openTextDocument(launchPath);
  await vscode.window.showTextDocument(doc);
  vscode.window.showInformationMessage('已生成 launch.json（使用 CodeLLDB）。按 F5 前请先安装 vadimcn.vscode-lldb。');
}
