// Author: 张晓达
import * as vscode from 'vscode';
import { disposeAllDiagnostics } from './diagnostics';
import {
  getLspLog,
  isLspRunning,
  restartLanguageServer,
  startLanguageServer,
  stopLanguageServer,
  disposeLspUi,
} from './lspClient';
import {
  fileBelongsToProject,
  findProjectForFile,
  manifestPath,
  pickProject,
} from './project';
import {
  generateLaunchJson,
  JNativeDebugConfigurationProvider,
} from './debugConfig';
import {
  buildCurrentFile,
  buildProject,
  createMobileProject,
  createProject,
  debugProject,
  disposeRunner,
  getOutput,
  mobileBuild,
  mobileDoctor,
  mobileRun,
  resolveDebugProgram,
  runProject,
  stopRun,
} from './runner';
import { registerTaskProvider } from './tasks';
import { checkToolchain, promptMissingToolchain, resolveToolchain } from './toolchain';
import {
  disposeTargetStatus,
  refreshTargetStatus,
  selectTargetCommand,
} from './targetContext';

export async function activate(context: vscode.ExtensionContext): Promise<void> {
  registerTaskProvider(context);
  await refreshTargetStatus(context);

  context.subscriptions.push(
    vscode.debug.registerDebugConfigurationProvider(
      'lldb',
      new JNativeDebugConfigurationProvider(),
      vscode.DebugConfigurationProviderTriggerKind.Dynamic
    ),
    vscode.debug.registerDebugConfigurationProvider(
      'lldb',
      new JNativeDebugConfigurationProvider(),
      vscode.DebugConfigurationProviderTriggerKind.Initial
    )
  );

  context.subscriptions.push(
    vscode.commands.registerCommand('jnative.openAsJNative', async () => {
      const editor = vscode.window.activeTextEditor;
      if (!editor) {
        return;
      }
      await vscode.languages.setTextDocumentLanguage(editor.document, 'jnative');
    }),
    vscode.commands.registerCommand('jnative.checkToolchain', async () => {
      if (!vscode.workspace.isTrusted) {
        vscode.window.showWarningMessage('工作区未受信任：仅允许基础高亮，工具链检查被跳过。');
        return;
      }
      const result = await checkToolchain(vscode.workspace.workspaceFolders);
      const channel = getOutput();
      channel.clear();
      channel.show(true);
      for (const line of result.lines) {
        channel.appendLine(line);
      }
      if (!result.ok) {
        await promptMissingToolchain('工具链检查未通过，请查看 JNative 输出。');
      } else {
        vscode.window.showInformationMessage('JNative 工具链检查完成（见输出面板）。');
      }
    }),
    vscode.commands.registerCommand('jnative.createProject', () => createProject()),
    vscode.commands.registerCommand('jnative.createMobileProject', () => createMobileProject()),
    vscode.commands.registerCommand('jnative.mobileDoctor', () => mobileDoctor()),
    vscode.commands.registerCommand('jnative.mobileBuild', async () => {
      const editor = vscode.window.activeTextEditor?.document.uri.fsPath;
      const project = await pickProject(vscode.workspace.workspaceFolders, editor);
      if (!project) {
        return;
      }
      await mobileBuild(project);
    }),
    vscode.commands.registerCommand('jnative.mobileRun', async () => {
      const editor = vscode.window.activeTextEditor?.document.uri.fsPath;
      const project = await pickProject(vscode.workspace.workspaceFolders, editor);
      if (!project) {
        return;
      }
      await mobileRun(project);
    }),
    vscode.commands.registerCommand('jnative.buildProject', async () => {
      const editor = vscode.window.activeTextEditor?.document.uri.fsPath;
      const project = await pickProject(vscode.workspace.workspaceFolders, editor);
      if (!project) {
        return;
      }
      const r = await buildProject(project);
      if (r.success) {
        vscode.window.showInformationMessage('构建成功');
      }
      // failures already notify with Problems / Output actions
    }),
    vscode.commands.registerCommand('jnative.runProject', async () => {
      const editor = vscode.window.activeTextEditor?.document.uri.fsPath;
      const project = await pickProject(vscode.workspace.workspaceFolders, editor);
      if (!project) {
        return;
      }
      await runProject(project);
    }),
    vscode.commands.registerCommand('jnative.showLanguageServerLog', () => {
      getLspLog().show(true);
    }),
    vscode.commands.registerCommand('jnative.restartLanguageServer', async () => {
      await restartLanguageServer(context);
      vscode.window.showInformationMessage(
        isLspRunning() ? 'JNative 语言服务已重启' : 'JNative 语言服务重启未成功 — 请查看语言服务日志'
      );
    }),
    vscode.commands.registerCommand('jnative.debugProject', async () => {
      const editor = vscode.window.activeTextEditor?.document.uri.fsPath;
      const project = await pickProject(vscode.workspace.workspaceFolders, editor);
      if (!project) {
        return;
      }
      await debugProject(project);
    }),
    vscode.commands.registerCommand('jnative.generateLaunchJson', () => generateLaunchJson()),
    vscode.commands.registerCommand('jnative.resolveDebugProgram', () => resolveDebugProgram()),
    vscode.commands.registerCommand('jnative.buildFile', async () => {
      const editor = vscode.window.activeTextEditor;
      if (!editor) {
        vscode.window.showWarningMessage('无当前文件');
        return;
      }
      const r = await buildCurrentFile(editor.document.uri.fsPath);
      if (r.success) {
        vscode.window.showInformationMessage('文件构建成功');
      }
    }),
    vscode.commands.registerCommand('jnative.stopRun', () => {
      stopRun();
      vscode.window.showInformationMessage('已请求停止运行');
    }),
    vscode.commands.registerCommand('jnative.showOutput', () => getOutput().show(true)),
    vscode.commands.registerCommand('jnative.openManifest', async () => {
      const editor = vscode.window.activeTextEditor?.document.uri.fsPath;
      const project = await pickProject(vscode.workspace.workspaceFolders, editor);
      if (!project) {
        return;
      }
      const doc = await vscode.workspace.openTextDocument(manifestPath(project));
      await vscode.window.showTextDocument(doc);
    }),
    vscode.commands.registerCommand('jnative.selectTarget', () => selectTargetCommand(context))
  );

  context.subscriptions.push(
    vscode.workspace.onDidOpenTextDocument((doc) => void maybeSetJNativeLanguage(doc)),
    vscode.window.onDidChangeActiveTextEditor((e) => {
      if (e) {
        void maybeSetJNativeLanguage(e.document);
        void refreshTargetStatus(context);
      }
    }),
    vscode.workspace.onDidChangeConfiguration((e) => {
      if (e.affectsConfiguration('jnative.target')) {
        void refreshTargetStatus(context);
      }
    })
  );

  for (const doc of vscode.workspace.textDocuments) {
    void maybeSetJNativeLanguage(doc);
  }

  context.subscriptions.push(
    vscode.workspace.onDidGrantWorkspaceTrust(async () => {
      await startLanguageServer(context);
    })
  );

  if (vscode.workspace.isTrusted) {
    await startLanguageServer(context);
  }
}

export async function deactivate(): Promise<void> {
  await stopLanguageServer();
  disposeRunner();
  disposeAllDiagnostics();
  disposeLspUi();
  disposeTargetStatus();
}

async function maybeSetJNativeLanguage(doc: vscode.TextDocument): Promise<void> {
  if (doc.uri.scheme !== 'file') {
    return;
  }
  const lower = doc.fileName.toLowerCase();
  const isJn = lower.endsWith('.jn');
  const isJava = lower.endsWith('.java');
  if (!isJn && !isJava) {
    return;
  }
  if (doc.languageId === 'jnative') {
    return;
  }
  if (isJn) {
    // .jn is always JNative (contributes.languages also maps it).
    await vscode.languages.setTextDocumentLanguage(doc, 'jnative');
    return;
  }
  const project = findProjectForFile(doc.fileName);
  if (!project) {
    return;
  }
  if (!fileBelongsToProject(doc.fileName, project)) {
    return;
  }
  // Avoid stealing language from ordinary Java projects: only auto-switch inside
  // recognized JNative source roots.
  await vscode.languages.setTextDocumentLanguage(doc, 'jnative');
}

/** Test helper exports */
export const _test = {
  findProjectForFile,
  resolveToolchain,
  isLspRunning,
};
