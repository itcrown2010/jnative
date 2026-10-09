// Author: 张晓达
import * as vscode from 'vscode';
import {
  discoverProjectsUnder,
  fileBelongsToProject as belongs,
  findProjectForFile as findForFile,
  isProjectRoot as isRoot,
  JNativeProjectInfo,
  loadProject as load,
  manifestPath as manifest,
} from './projectDetect';

export type JNativeProject = JNativeProjectInfo;

export const isProjectRoot = isRoot;
export const loadProject = load;
export const fileBelongsToProject = belongs;
export const findProjectForFile = findForFile;
export const manifestPath = manifest;

export function discoverWorkspaceProjects(
  folders: readonly vscode.WorkspaceFolder[] | undefined
): JNativeProject[] {
  return discoverProjectsUnder((folders ?? []).map((f) => f.uri.fsPath));
}

export async function pickProject(
  folders: readonly vscode.WorkspaceFolder[] | undefined,
  preferredFile?: string
): Promise<JNativeProject | undefined> {
  if (preferredFile) {
    const forFile = findForFile(preferredFile);
    if (forFile) {
      return forFile;
    }
  }
  const all = discoverWorkspaceProjects(folders);
  if (all.length === 0) {
    vscode.window.showWarningMessage(
      '未找到 JNative 项目（需要 jnative.project，或 jnative.toml + src/）。请先打开含项目根的工作区文件夹。'
    );
    return undefined;
  }
  if (all.length === 1) {
    return all[0];
  }
  const picked = await vscode.window.showQuickPick(
    all.map((p) => ({
      label: p.name,
      description: p.root,
      detail: preferredFile && preferredFile.startsWith(p.root)
        ? '当前文件所属项目'
        : undefined,
      project: p,
    })),
    {
      placeHolder: '多个 JNative 项目 — 请选择构建/运行目标（取消则不继续）',
      ignoreFocusOut: true,
    }
  );
  if (!picked) {
    vscode.window.showInformationMessage('已取消：未选择项目，不会启动构建或运行。');
    return undefined;
  }
  return picked.project;
}
