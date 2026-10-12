/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

// Author: 张晓达
import * as vscode from 'vscode';
import { findProjectForFile, JNativeProject, pickProject } from './project';

export type JNativeTargetTriple = 'native' | 'wasm32-browser';

let statusItem: vscode.StatusBarItem | undefined;

export function getConfiguredTarget(folder?: vscode.WorkspaceFolder): JNativeTargetTriple {
  const cfg = folder
    ? vscode.workspace.getConfiguration('jnative', folder.uri)
    : vscode.workspace.getConfiguration('jnative');
  const raw = (cfg.get<string>('target') || 'native').trim().toLowerCase();
  if (raw === 'wasm32-browser' || raw === 'wasm32' || raw === 'browser' || raw === 'wasm') {
    return 'wasm32-browser';
  }
  return 'native';
}

export async function setConfiguredTarget(
  triple: JNativeTargetTriple,
  folder?: vscode.WorkspaceFolder
): Promise<void> {
  const cfg = folder
    ? vscode.workspace.getConfiguration('jnative', folder.uri)
    : vscode.workspace.getConfiguration('jnative');
  await cfg.update('target', triple, folder ? vscode.ConfigurationTarget.WorkspaceFolder : vscode.ConfigurationTarget.Workspace);
}

export function ensureTargetStatus(context: vscode.ExtensionContext): vscode.StatusBarItem {
  if (!statusItem) {
    statusItem = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left, 49);
    statusItem.command = 'jnative.selectTarget';
    context.subscriptions.push(statusItem);
    statusItem.show();
  }
  return statusItem;
}

export async function refreshTargetStatus(context: vscode.ExtensionContext): Promise<void> {
  const item = ensureTargetStatus(context);
  const editor = vscode.window.activeTextEditor?.document.uri.fsPath;
  const project = editor
    ? findProjectForFile(editor)
    : await pickProject(vscode.workspace.workspaceFolders, undefined);
  const folder = project
    ? vscode.workspace.getWorkspaceFolder(vscode.Uri.file(project.root))
    : vscode.workspace.workspaceFolders?.[0];
  const target = getConfiguredTarget(folder);
  const name = project ? project.name || project.root : '(no project)';
  item.text = `$(target) JNative: ${shortName(name)} · ${target}`;
  item.tooltip = project
    ? `项目: ${project.root}\n目标: ${target}\n点击切换目标（构建/运行/诊断共用）`
    : `目标: ${target}\n点击切换目标`;
}

function shortName(name: string): string {
  const parts = name.replace(/\\/g, '/').split('/');
  return parts[parts.length - 1] || name;
}

export async function selectTargetCommand(context: vscode.ExtensionContext): Promise<void> {
  const editor = vscode.window.activeTextEditor?.document.uri.fsPath;
  const project = await pickProject(vscode.workspace.workspaceFolders, editor);
  const folder = project
    ? vscode.workspace.getWorkspaceFolder(vscode.Uri.file(project.root))
    : vscode.workspace.workspaceFolders?.[0];
  const pick = await vscode.window.showQuickPick(
    [
      {
        label: 'native',
        description: '主机原生 (clang/LLVM)',
        detail: '文件系统 / 线程 / FFI / 桌面 UI',
      },
      {
        label: 'wasm32-browser',
        description: '浏览器 WASM',
        detail: 'DOM / JS interop / fetch — 无主机文件与线程；移动容器亦编译为此目标',
      },
    ],
    { title: '选择 JNative 编译目标', placeHolder: getConfiguredTarget(folder) }
  );
  if (!pick) {
    return;
  }
  await setConfiguredTarget(pick.label as JNativeTargetTriple, folder);
  await refreshTargetStatus(context);
  vscode.window.showInformationMessage(
    `JNative 目标已切换为 ${pick.label}（Problems / 构建 / 运行将使用该目标）`
  );
  // Ask LSP to re-analyze with new target when supported.
  await vscode.commands.executeCommand('jnative.restartLanguageServer');
}

export function disposeTargetStatus(): void {
  statusItem?.dispose();
  statusItem = undefined;
}

/** Test helper */
export function _testProjectTarget(project: JNativeProject | undefined, folder?: vscode.WorkspaceFolder): string {
  return getConfiguredTarget(folder);
}
