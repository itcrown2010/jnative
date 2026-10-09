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
      '未找到 JNative 项目（需要 jnative.project，或 jnative.toml + src/）。'
    );
    return undefined;
  }
  if (all.length === 1) {
    return all[0];
  }
  const picked = await vscode.window.showQuickPick(
    all.map((p) => ({ label: p.name, description: p.root, project: p })),
    { placeHolder: '选择 JNative 项目' }
  );
  return picked?.project;
}
