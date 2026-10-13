/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

// Author: 张晓达
import * as fs from 'fs';
import * as path from 'path';
import { ChildProcess } from 'child_process';
import * as vscode from 'vscode';
import {
  applyJsonDiagnostics,
  clearProjectDiagnostics,
  parseBuildReport,
  parseTextDiagnostics,
} from './diagnostics';
import { findProjectForFile, JNativeProject } from './project';
import {
  promptMissingToolchain,
  resolveToolchain,
  runCapture,
  spawnToolchain,
  ToolchainInvocation,
} from './toolchain';
import { getOutput, disposeOutput } from './output';
import { detectProjectKind } from './projectKind';
import { rememberProject } from './recentProjects';
import { getConfiguredTarget } from './targetContext';
import { startWebPreview, stopPreview } from './webPreview';
import {
  classifyMobileFailure,
  extractCompileDiagnostics,
  getLastMobileSession,
  layerLog,
  newRequestId,
  parseDevicesJson,
  pickConfiguration,
  pickDevice,
  pickMobileTarget,
  setLastMobileSession,
  type MobileConfiguration,
  type MobileTarget,
} from './mobileFlow';

export { getOutput };

const buildLocks = new Map<string, Promise<unknown>>();
let runProcess: ChildProcess | undefined;

export function disposeRunner(): void {
  stopRun();
  disposeOutput();
}

async function withProjectLock<T>(root: string, fn: () => Promise<T>): Promise<T> {
  const key = path.resolve(root);
  const prev = buildLocks.get(key) ?? Promise.resolve();
  let release!: () => void;
  const gate = new Promise<void>((r) => {
    release = r;
  });
  const chained = prev.then(() => gate);
  buildLocks.set(
    key,
    chained.catch(() => undefined)
  );
  await prev.catch(() => undefined);
  try {
    return await fn();
  } finally {
    release();
  }
}

async function requireToolchain(): Promise<ToolchainInvocation | undefined> {
  if (!vscode.workspace.isTrusted) {
    vscode.window.showWarningMessage('工作区未受信任：已禁止启动 JNative 编译器或运行程序。');
    return undefined;
  }
  const resolved = await resolveToolchain(vscode.workspace.workspaceFolders);
  if (!resolved.invocation) {
    await promptMissingToolchain(resolved.missing || '工具链未配置');
    return undefined;
  }
  return resolved.invocation;
}

/**
 * Build/run read files from disk — save dirty editors first so output matches the UI.
 * @returns false if the user cancelled or a save failed (must not compile stale buffers).
 */
async function saveDirtySources(hintPath?: string): Promise<boolean> {
  const dirty = vscode.workspace.textDocuments.filter((doc) => {
    if (doc.isUntitled || !doc.isDirty || doc.uri.scheme !== 'file') {
      return false;
    }
    const name = doc.fileName.toLowerCase();
    if (!(name.endsWith('.jn') || name.endsWith('.java') || name.endsWith('.toml') || name.endsWith('.project'))) {
      return false;
    }
    if (hintPath) {
      const root = hintPath;
      return doc.uri.fsPath === root || doc.uri.fsPath.startsWith(root + path.sep);
    }
    return true;
  });
  if (dirty.length === 0) {
    return true;
  }
  for (const doc of dirty) {
    const ok = await doc.save();
    if (!ok || doc.isDirty) {
      vscode.window.showErrorMessage(`无法保存 ${path.basename(doc.fileName)}，已取消构建（不会编译旧内容）。`);
      return false;
    }
  }
  return true;
}

async function notifyBuildFailure(
  channel: vscode.OutputChannel,
  summary: string,
  orphanMessages: string[]
): Promise<void> {
  for (const m of orphanMessages) {
    channel.appendLine(`[tool] ${m}`);
  }
  const pick = await vscode.window.showErrorMessage(summary, '查看 Problems', '查看输出');
  if (pick === '查看 Problems') {
    await vscode.commands.executeCommand('workbench.actions.view.problems');
  } else if (pick === '查看输出') {
    channel.show(true);
  }
}

export interface BuildResult {
  success: boolean;
  artifact?: string;
}

export async function buildProject(project: JNativeProject, debug = false): Promise<BuildResult> {
  const inv = await requireToolchain();
  if (!inv) {
    return { success: false };
  }
  if (!(await saveDirtySources(project.root))) {
    return { success: false };
  }
  const kind = detectProjectKind(project);
  if (kind === 'web') {
    return buildWebArtifact(project);
  }
  return withProjectLock(project.root, async () => {
    const channel = getOutput();
    channel.show(true);
    channel.appendLine(`[build${debug ? ' --debug' : ''}] ${project.root}`);
    const folder = vscode.workspace.getWorkspaceFolder(vscode.Uri.file(project.root));
    const cfg = vscode.workspace.getConfiguration('jnative', folder?.uri);
    const extra = cfg.get<string[]>('buildArguments') ?? [];
    const target = kind === 'desktop' ? 'native' : getConfiguredTarget(folder);
    const args = [
      'build',
      '--project',
      project.root,
      '--target',
      target,
      '--format',
      'json',
      ...(debug ? ['--debug'] : []),
      ...extra,
    ];
    channel.appendLine(`[target] ${target}`);
    const result = await runCapture(inv, args, { cwd: project.root, timeoutMs: 300000 });
    channel.appendLine(result.stdout);
    if (result.stderr.trim()) {
      channel.appendLine(result.stderr);
    }
    channel.appendLine(`[exit] ${result.code}`);
    if (result.timedOut) {
      await notifyBuildFailure(channel, '构建超时（已终止进程，不视为成功）', []);
      return { success: false };
    }
    const report = parseBuildReport(result.stdout);
    if (report) {
      if (report.success) {
        clearProjectDiagnostics(project.root);
      } else {
        const { applied, orphanMessages } = applyJsonDiagnostics(
          project.root,
          report.diagnostics,
          project.root
        );
        await notifyBuildFailure(
          channel,
          applied > 0
            ? `构建失败（${applied} 条可定位诊断已写入 Problems）`
            : `构建失败：${orphanMessages[0] || '见输出日志'}`,
          orphanMessages
        );
      }
      if (report.success && report.artifact && fs.existsSync(report.artifact)) {
        channel.appendLine(`[artifact] ${report.artifact}`);
        return { success: true, artifact: report.artifact };
      }
      if (report.success) {
        channel.appendLine('[build] success but artifact missing');
        await notifyBuildFailure(channel, '构建报告成功但找不到产物', []);
        return { success: false };
      }
      return { success: false };
    }
    const textDiags = parseTextDiagnostics(result.stdout + '\n' + result.stderr);
    if (result.code === 0) {
      clearProjectDiagnostics(project.root);
      const outLine = (result.stdout + result.stderr).match(/Output:\s+(\S+)/);
      if (outLine) {
        const art = path.isAbsolute(outLine[1])
          ? outLine[1]
          : path.resolve(project.root, outLine[1]);
        if (fs.existsSync(art)) {
          return { success: true, artifact: art };
        }
      }
      channel.appendLine('[build] success but could not locate artifact');
      await notifyBuildFailure(channel, '构建退出 0 但无法定位产物', []);
      return { success: false };
    }
    const { applied, orphanMessages } = applyJsonDiagnostics(project.root, textDiags, project.root);
    const toolHint =
      /clang|linker|ld:|not found|No such file/i.test(result.stderr + result.stdout)
        ? '（可能是工具链/链接错误，见输出）'
        : '';
    await notifyBuildFailure(
      channel,
      applied > 0
        ? `构建失败${toolHint}（Problems 已更新）`
        : `构建失败${toolHint}：${orphanMessages[0] || (result.stderr || result.stdout).trim().split('\n')[0] || '见输出'}`,
      orphanMessages
    );
    return { success: false };
  });
}

export async function buildCurrentFile(filePath: string): Promise<BuildResult> {
  const inv = await requireToolchain();
  if (!inv) {
    return { success: false };
  }
  const channel = getOutput();
  channel.show(true);
  /* Keep binaries out of source roots — project build would otherwise treat
     src/.jnative-file-build/Main.jn (Mach-O) as source and fail with UTF-8 errors. */
  const project = findProjectForFile(filePath);
  const projectRoot = project?.root ?? path.dirname(filePath);
  if (!(await saveDirtySources(projectRoot))) {
    return { success: false };
  }
  const outDir = path.join(projectRoot, '.jnative', 'file-build');
  fs.mkdirSync(outDir, { recursive: true });
  const fileName = path.basename(filePath);
  const base = fileName.replace(/\.(java|jn)$/i, '');
  const outFile = path.join(outDir, base);
  const folder = vscode.workspace.getWorkspaceFolder(vscode.Uri.file(projectRoot));
  const target = getConfiguredTarget(folder);
  channel.appendLine(`[build-file] ${filePath}`);
  channel.appendLine(`[target] ${target}`);
  const result = await runCapture(
    inv,
    [filePath, '-o', outFile, '--target', target, '--format', 'json'],
    { cwd: projectRoot, timeoutMs: 300000 }
  );
  channel.appendLine(result.stdout);
  if (result.stderr.trim()) {
    channel.appendLine(result.stderr);
  }
  channel.appendLine(`[exit] ${result.code}`);
  const report = parseBuildReport(result.stdout);
  if (report) {
    if (report.success) {
      clearProjectDiagnostics(projectRoot);
      const art = report.artifact && fs.existsSync(report.artifact) ? report.artifact : outFile;
      return { success: true, artifact: art };
    }
    const { applied, orphanMessages } = applyJsonDiagnostics(
      projectRoot,
      report.diagnostics,
      projectRoot
    );
    await notifyBuildFailure(
      channel,
      applied > 0
        ? `文件构建失败（${applied} 条诊断已写入 Problems）`
        : `文件构建失败：${orphanMessages[0] || '见输出'}`,
      orphanMessages
    );
    return { success: false };
  }
  if (result.code === 0 && fs.existsSync(outFile)) {
    clearProjectDiagnostics(projectRoot);
    return { success: true, artifact: outFile };
  }
  const textDiags = parseTextDiagnostics(result.stdout + '\n' + result.stderr);
  const { applied, orphanMessages } = applyJsonDiagnostics(projectRoot, textDiags, projectRoot);
  await notifyBuildFailure(
    channel,
    applied > 0 ? '文件构建失败（Problems 已更新）' : '文件构建失败（见输出）',
    orphanMessages
  );
  return { success: false };
}

export async function runProject(project: JNativeProject): Promise<void> {
  rememberProject(project.root);
  const kind = detectProjectKind(project);
  if (kind === 'web') {
    await runWebProject(project);
    return;
  }
  if (kind === 'mobile') {
    await mobileRun(project);
    return;
  }
  const built = await buildProject(project, false);
  if (!built.success || !built.artifact) {
    vscode.window.showErrorMessage('构建失败，未启动运行（不会使用旧产物）。');
    return;
  }
  await runArtifact(built.artifact, project.root);
}

/** Build wasm32-browser entry and preview dist/ (does not run stale dist on failure). */
export async function runWebProject(project: JNativeProject): Promise<void> {
  const inv = await requireToolchain();
  if (!inv) {
    return;
  }
  if (!(await saveDirtySources(project.root))) {
    return;
  }
  return withProjectLock(project.root, async () => {
    const channel = getOutput();
    channel.show(true);
    const entry = resolveWebEntry(project.root);
    if (!entry) {
      vscode.window.showErrorMessage('未找到 Web 入口（src/App.java 或 src/App.jn）');
      return;
    }
    const dist = path.join(project.root, 'dist');
    // Clear prior preview for this project only after we intend to rebuild
    fs.mkdirSync(dist, { recursive: true });
    const stamp = path.join(dist, '.jnative-build-ok');
    if (fs.existsSync(stamp)) {
      fs.unlinkSync(stamp);
    }
    channel.appendLine(`[web-build] ${entry} → ${dist}`);
    const result = await runCapture(
      inv,
      [entry, '--target', 'wasm32-browser', '-o', dist, '--format', 'json'],
      { cwd: project.root, timeoutMs: 300000 }
    );
    channel.appendLine(result.stdout);
    if (result.stderr.trim()) {
      channel.appendLine(result.stderr);
    }
    const report = parseBuildReport(result.stdout);
    if (report && !report.success) {
      applyJsonDiagnostics(project.root, report.diagnostics, project.root);
      vscode.window.showErrorMessage('Web 构建失败，未启动预览（不会使用旧 dist）。');
      return;
    }
    if (result.code !== 0 && !report?.success) {
      vscode.window.showErrorMessage('Web 构建失败，未启动预览。');
      return;
    }
    const wasm = path.join(dist, 'app.wasm');
    const js = path.join(dist, 'app.js');
    if (!fs.existsSync(wasm) || !fs.existsSync(js)) {
      vscode.window.showErrorMessage('构建未产出 app.wasm/app.js，未启动预览。');
      return;
    }
    const indexSrc = path.join(project.root, 'index.html');
    if (fs.existsSync(indexSrc)) {
      fs.copyFileSync(indexSrc, path.join(dist, 'index.html'));
    }
    if (!fs.existsSync(path.join(dist, 'index.html'))) {
      vscode.window.showErrorMessage('缺少 index.html，未启动预览。');
      return;
    }
    fs.writeFileSync(stamp, new Date().toISOString(), 'utf8');
    clearProjectDiagnostics(project.root);
    const preview = await startWebPreview(dist, channel);
    if (preview) {
      vscode.window.showInformationMessage(`Web 预览已启动 ${preview.url}`);
    }
  });
}

async function runMobileWebPreview(project: JNativeProject): Promise<void> {
  const inv = await requireToolchain();
  if (!inv) {
    return;
  }
  const channel = getOutput();
  channel.show(true);
  const result = await runCapture(
    inv,
    ['mobile', 'build', '--target', 'web', '--project', project.root],
    { cwd: project.root, timeoutMs: 600000 }
  );
  channel.appendLine(result.stdout + result.stderr);
  if (result.code !== 0) {
    vscode.window.showErrorMessage('移动 Web 构建失败，未启动预览。');
    return;
  }
  const dist = path.join(project.root, 'dist');
  if (!fs.existsSync(path.join(dist, 'index.html'))) {
    vscode.window.showErrorMessage(`未找到 ${dist}/index.html`);
    return;
  }
  const preview = await startWebPreview(dist, channel);
  if (preview) {
    vscode.window.showInformationMessage(`移动 Web 预览 ${preview.url}`);
  }
}

function resolveWebEntry(root: string): string | undefined {
  for (const rel of ['src/App.java', 'src/App.jn', 'src/Main.java', 'src/Main.jn']) {
    const p = path.join(root, rel);
    if (fs.existsSync(p)) {
      return p;
    }
  }
  return undefined;
}

/** Package project artifacts; never auto-publish / notarize. */
export async function packageProject(project: JNativeProject): Promise<void> {
  const kind = detectProjectKind(project);
  const channel = getOutput();
  channel.show(true);
  channel.appendLine(`[package] kind=${kind} root=${project.root}`);
  const lines: string[] = [
    'JNative 打包（不会自动发布到商店或注册表）：',
    '',
  ];
  if (kind === 'web') {
    lines.push(
      '产物目录: dist/（app.wasm + app.js + index.html）',
      '分发: 可将 dist/ 部署到任意静态主机。',
      '限制: 无应用商店签名；依赖浏览器 WASM 支持。'
    );
    for (const l of lines) {
      channel.appendLine(l);
    }
    vscode.window.showInformationMessage('打包说明已写入 JNative 输出面板（未自动发布）。');
    return;
  }
  if (kind === 'mobile') {
    await packageMobileProject(project);
    return;
  }

  /* Desktop: rebuild then pack .app via scripts/macos/pack-app.sh (same pipeline as CLI). */
  const packScript = locatePackAppScript(project.root);
  if (!packScript) {
    channel.appendLine('error: 找不到 scripts/macos/pack-app.sh');
    vscode.window.showErrorMessage('未找到 scripts/macos/pack-app.sh；无法打包 .app。');
    return;
  }
  channel.appendLine('先构建原生 GUI 产物（失败则不保留/不启动旧 .app）…');
  const built = await buildProject(project, false);
  if (!built.success || !built.artifact || !fs.existsSync(built.artifact)) {
    channel.appendLine('error: 构建失败或产物缺失，已中止打包。');
    vscode.window.showErrorMessage('构建失败，未打包 .app。');
    return;
  }
  const artifact = built.artifact;
  const staleApp = artifact + '.app';
  if (fs.existsSync(staleApp)) {
    try {
      fs.rmSync(staleApp, { recursive: true, force: true });
      channel.appendLine(`removed stale ${staleApp}`);
    } catch (e) {
      channel.appendLine(`warn: could not remove stale app: ${String(e)}`);
    }
  }
  const appName = path.basename(artifact);
  const outDir = path.dirname(artifact);
  const bundleId = `io.jnative.app.${appName.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`;
  channel.appendLine(`pack: ${packScript}`);
  channel.appendLine(`bin:  ${artifact}`);
  try {
    const { spawnSync } = await import('child_process');
    const r = spawnSync(
      packScript,
      ['--bin', artifact, '--name', appName, '--id', bundleId, '--out', outDir, '--version', '0.1.0'],
      { encoding: 'utf8' }
    );
    if (r.stdout) {
      channel.appendLine(r.stdout.trim());
    }
    if (r.stderr) {
      channel.appendLine(r.stderr.trim());
    }
    if (r.status !== 0) {
      channel.appendLine(`pack failed status=${r.status}`);
      vscode.window.showErrorMessage('打包失败（见输出面板）。');
      return;
    }
    const appPath = path.join(outDir, `${appName}.app`);
    channel.appendLine(`产物: ${appPath}`);
    channel.appendLine('限制: 未签名/未公证；仅本机验证环境可双击运行。');
    vscode.window.showInformationMessage(`已打包: ${appPath}`);
  } catch (e) {
    channel.appendLine(`pack exception: ${String(e)}`);
    vscode.window.showErrorMessage('打包过程异常（见输出面板）。');
  }
}

/** Walk up from project root to find repo scripts/macos/pack-app.sh. */
function locatePackAppScript(start: string): string | undefined {
  let dir = path.resolve(start);
  for (let i = 0; i < 8; i++) {
    const candidate = path.join(dir, 'scripts', 'macos', 'pack-app.sh');
    if (fs.existsSync(candidate)) {
      return candidate;
    }
    const parent = path.dirname(dir);
    if (parent === dir) {
      break;
    }
    dir = parent;
  }
  return undefined;
}

export async function openTutorial(): Promise<void> {
  const candidates = [
    path.resolve(__dirname, '..', '..', '..', 'docs', 'tutorial', 'index.md'),
    path.resolve(__dirname, '..', '..', '..', 'docs', 'tutorial', 'chapters', '01-jnative-是什么.md'),
  ];
  // Also search from workspace folders
  for (const f of vscode.workspace.workspaceFolders ?? []) {
    candidates.unshift(
      path.join(f.uri.fsPath, 'docs', 'tutorial', 'index.md'),
      path.join(f.uri.fsPath, 'docs', 'tutorial', 'chapters', '01-jnative-是什么.md')
    );
  }
  for (const c of candidates) {
    if (fs.existsSync(c)) {
      await vscode.window.showTextDocument(vscode.Uri.file(c));
      return;
    }
  }
  // Bundled fallback inside extension
  const bundled = path.join(
    vscode.extensions.getExtension('jnative.jnative')?.extensionPath || path.resolve(__dirname, '..'),
    'docs',
    'GETTING_STARTED.md'
  );
  if (fs.existsSync(bundled)) {
    await vscode.window.showTextDocument(vscode.Uri.file(bundled));
    return;
  }
  vscode.window.showWarningMessage('未找到教程文件。请打开仓库 docs/tutorial/（无虚构在线链接）。');
}

export async function openExamplesDoc(): Promise<void> {
  const candidates: string[] = [];
  for (const f of vscode.workspace.workspaceFolders ?? []) {
    candidates.push(path.join(f.uri.fsPath, 'examples', 'guide-hello', 'src', 'Main.jn'));
    candidates.push(path.join(f.uri.fsPath, 'docs', 'tutorial', 'index.md'));
  }
  candidates.push(path.resolve(__dirname, '..', '..', '..', 'examples', 'guide-hello', 'src', 'Main.jn'));
  for (const c of candidates) {
    if (fs.existsSync(c)) {
      await vscode.window.showTextDocument(vscode.Uri.file(c));
      return;
    }
  }
  vscode.window.showWarningMessage('未找到示例路径。');
}

async function buildWebArtifact(project: JNativeProject): Promise<BuildResult> {
  const inv = await requireToolchain();
  if (!inv) {
    return { success: false };
  }
  return withProjectLock(project.root, async () => {
    const channel = getOutput();
    channel.show(true);
    const entry = resolveWebEntry(project.root);
    if (!entry) {
      await notifyBuildFailure(channel, '未找到 Web 入口源码', []);
      return { success: false };
    }
    const dist = path.join(project.root, 'dist');
    fs.mkdirSync(dist, { recursive: true });
    channel.appendLine(`[web-build] ${entry}`);
    const result = await runCapture(
      inv,
      [entry, '--target', 'wasm32-browser', '-o', dist, '--format', 'json'],
      { cwd: project.root, timeoutMs: 300000 }
    );
    channel.appendLine(result.stdout);
    if (result.stderr.trim()) {
      channel.appendLine(result.stderr);
    }
    const report = parseBuildReport(result.stdout);
    if (report && !report.success) {
      applyJsonDiagnostics(project.root, report.diagnostics, project.root);
      await notifyBuildFailure(channel, 'Web 构建失败', []);
      return { success: false };
    }
    if (result.code !== 0) {
      await notifyBuildFailure(channel, 'Web 构建失败', []);
      return { success: false };
    }
    const indexSrc = path.join(project.root, 'index.html');
    if (fs.existsSync(indexSrc)) {
      fs.copyFileSync(indexSrc, path.join(dist, 'index.html'));
    }
    const wasm = path.join(dist, 'app.wasm');
    if (!fs.existsSync(wasm)) {
      await notifyBuildFailure(channel, '未产出 app.wasm', []);
      return { success: false };
    }
    clearProjectDiagnostics(project.root);
    return { success: true, artifact: wasm };
  });
}

/** Build with --debug then launch CodeLLDB (vadimcn.vscode-lldb). */
export async function debugProject(project: JNativeProject): Promise<void> {
  if (!vscode.workspace.isTrusted) {
    vscode.window.showWarningMessage('工作区未受信任：禁止调试。');
    return;
  }
  const kind = detectProjectKind(project);
  if (kind === 'web' || kind === 'mobile') {
    vscode.window.showWarningMessage(
      kind === 'web'
        ? 'Web/WASM 当前不支持 CodeLLDB 源码调试。请使用 Problems 与浏览器 Console。'
        : '移动容器当前不支持扩展内 LLDB 调试。请使用平台日志与 `jnative mobile logs`。'
    );
    return;
  }
  const { ensureCodeLldb } = await import('./debugConfig');
  const { buildLaunchConfig, shouldRefuseStaleArtifact } = await import('./launchConfig');
  if (!(await ensureCodeLldb())) {
    return;
  }
  const built = await buildProject(project, true);
  if (shouldRefuseStaleArtifact(built.success, built.artifact, (p) => fs.existsSync(p))) {
    vscode.window.showErrorMessage('调试构建失败，未启动调试器（不会使用旧产物）。');
    return;
  }
  const artifact = built.artifact as string;
  const cfg = vscode.workspace.getConfiguration('jnative');
  const runArgs = cfg.get<string[]>('runArguments') ?? [];
  const env = cfg.get<Record<string, string>>('debugEnv') ?? {};
  const channel = getOutput();
  channel.appendLine(`[debug] ${artifact}`);
  const folder = vscode.workspace.getWorkspaceFolder(vscode.Uri.file(project.root));
  await vscode.debug.startDebugging(folder, buildLaunchConfig(project, artifact, runArgs, env));
}

/** Used by launch.json `${command:jnative.resolveDebugProgram}`. */
export async function resolveDebugProgram(): Promise<string> {
  if (!vscode.workspace.isTrusted) {
    throw new Error('工作区未受信任');
  }
  const editor = vscode.window.activeTextEditor?.document.uri.fsPath;
  const { pickProject } = await import('./project');
  const project = await pickProject(vscode.workspace.workspaceFolders, editor);
  if (!project) {
    throw new Error('未找到 JNative 项目');
  }
  const built = await buildProject(project, true);
  if (!built.success || !built.artifact || !fs.existsSync(built.artifact)) {
    throw new Error('调试构建失败，拒绝返回旧产物路径');
  }
  return built.artifact;
}

export async function runArtifact(artifact: string, cwd: string): Promise<void> {
  if (!vscode.workspace.isTrusted) {
    vscode.window.showWarningMessage('工作区未受信任：禁止运行。');
    return;
  }
  stopRun();
  const channel = getOutput();
  channel.show(true);
  const cfg = vscode.workspace.getConfiguration('jnative');
  const runArgs = cfg.get<string[]>('runArguments') ?? [];
  channel.appendLine(`[run] ${artifact} ${runArgs.join(' ')}`);
  // Run binary directly — not via shell
  const { spawn } = await import('child_process');
  runProcess = spawn(artifact, runArgs, { cwd, shell: false });
  runProcess.stdout?.on('data', (d: Buffer) => channel.append(d.toString('utf8')));
  runProcess.stderr?.on('data', (d: Buffer) => channel.append(d.toString('utf8')));
  runProcess.on('close', (code) => {
    channel.appendLine(`[exit] ${code}`);
    runProcess = undefined;
  });
  runProcess.on('error', (err) => {
    channel.appendLine(`[run-error] ${err.message}`);
    runProcess = undefined;
  });
}

export function stopRun(): void {
  // Only stop processes / mobile sessions this extension started.
  if (runProcess && !runProcess.killed) {
    runProcess.kill('SIGTERM');
    const p = runProcess;
    setTimeout(() => {
      if (p && !p.killed) {
        p.kill('SIGKILL');
      }
    }, 2000);
  }
  runProcess = undefined;
  stopPreview();
  void stopLastMobileSession();
}

async function stopLastMobileSession(): Promise<void> {
  const session = getLastMobileSession();
  if (!session) {
    return;
  }
  const inv = await requireToolchain();
  if (!inv) {
    return;
  }
  const channel = getOutput();
  layerLog(channel, session.requestId, session.target === 'android' ? 'android-container' : 'ios-container',
    `停止本次启动: ${session.target} ${session.deviceId}`);
  const result = await runCapture(
    inv,
    [
      'mobile',
      'stop',
      '--target',
      session.target,
      '--device',
      session.deviceId,
      '--project',
      session.projectRoot,
    ],
    { cwd: session.projectRoot, timeoutMs: 60000 }
  );
  channel.appendLine(result.stdout + result.stderr);
  setLastMobileSession(undefined);
}

/** Unified create wizard (desktop / web / mobile). */
export async function createProject(extensionPath?: string): Promise<void> {
  const { createFromWizard } = await import('./projectWizard');
  const ext =
    extensionPath ||
    vscode.extensions.getExtension('jnative.jnative')?.extensionPath ||
    path.resolve(__dirname, '..');
  await createFromWizard(ext);
}

export async function createDesktopProject(extensionPath?: string): Promise<void> {
  const { createFromWizard } = await import('./projectWizard');
  const ext =
    extensionPath ||
    vscode.extensions.getExtension('jnative.jnative')?.extensionPath ||
    path.resolve(__dirname, '..');
  await createFromWizard(ext, 'desktop');
}

export async function createWebProject(extensionPath?: string): Promise<void> {
  const { createFromWizard } = await import('./projectWizard');
  const ext =
    extensionPath ||
    vscode.extensions.getExtension('jnative.jnative')?.extensionPath ||
    path.resolve(__dirname, '..');
  await createFromWizard(ext, 'web');
}

export async function createMobileProject(extensionPath?: string): Promise<void> {
  const { createFromWizard } = await import('./projectWizard');
  const ext =
    extensionPath ||
    vscode.extensions.getExtension('jnative.jnative')?.extensionPath ||
    path.resolve(__dirname, '..');
  await createFromWizard(ext, 'mobile');
}

export async function mobileDoctor(): Promise<void> {
  const inv = await requireToolchain();
  if (!inv) {
    return;
  }
  const channel = getOutput();
  channel.show(true);
  const result = await runCapture(inv, ['doctor', '--mobile'], { timeoutMs: 120000 });
  channel.appendLine(result.stdout + result.stderr);
}

export async function mobileBuild(project: JNativeProject): Promise<void> {
  const inv = await requireToolchain();
  if (!inv) {
    return;
  }
  if (!(await saveDirtySources(project.root))) {
    return;
  }
  const target = (await pickMobileTarget()) as MobileTarget | undefined;
  if (!target) {
    return;
  }
  const configuration = (await pickConfiguration()) ?? 'debug';
  await withProjectLock(project.root, async () => {
    await runMobileBuild(project, inv, target, configuration, newRequestId());
  });
}

export async function mobileRun(project: JNativeProject): Promise<void> {
  const inv = await requireToolchain();
  if (!inv) {
    return;
  }
  if (!(await saveDirtySources(project.root))) {
    return;
  }
  const target = await pickMobileTarget();
  if (!target) {
    return;
  }
  const configuration = (await pickConfiguration()) ?? 'debug';
  const requestId = newRequestId();
  const channel = getOutput();
  channel.show(true);
  rememberProject(project.root);

  return withProjectLock(project.root, async () => {
    if (target === 'web') {
      const built = await runMobileBuild(project, inv, 'web', configuration, requestId);
      if (!built) {
        return;
      }
      const dist = path.join(project.root, 'dist');
      if (!fs.existsSync(path.join(dist, 'index.html'))) {
        layerLog(channel, requestId, 'wasm', `未找到 ${dist}/index.html，未启动预览`);
        vscode.window.showErrorMessage('Web 产物不完整，未启动预览。');
        return;
      }
      const preview = await startWebPreview(dist, channel);
      if (preview) {
        layerLog(channel, requestId, 'javascript', `预览 ${preview.url}`);
        vscode.window.showInformationMessage(`Web 预览 ${preview.url}`);
      }
      return;
    }

    // Doctor for the selected platform before build
    layerLog(channel, requestId, 'toolchain', `检查 ${target} 工具链…`);
    const doctor = await runCapture(inv, ['doctor', '--mobile'], {
      cwd: project.root,
      timeoutMs: 120000,
    });
    channel.appendLine(doctor.stdout + doctor.stderr);
    const doctorOk =
      target === 'android'
        ? /target android:\s*available/i.test(doctor.stdout)
        : /target ios:\s*available/i.test(doctor.stdout);
    if (!doctorOk) {
      layerLog(channel, requestId, 'toolchain', `${target} 工具链未就绪，已中止（不安装旧产物）`);
      vscode.window.showErrorMessage(`${target} 工具链未就绪。详见输出面板。`);
      return;
    }

    layerLog(channel, requestId, 'toolchain', `列出 ${target} 设备…`);
    const list = await runCapture(
      inv,
      ['mobile', 'devices', '--target', target, '--format', 'json'],
      { cwd: project.root, timeoutMs: 120000 }
    );
    channel.appendLine(list.stdout + list.stderr);
    if (list.code !== 0) {
      layerLog(channel, requestId, 'toolchain', '设备列表失败');
      vscode.window.showErrorMessage('无法列出设备。');
      return;
    }
    const devices = parseDevicesJson(list.stdout + '\n' + list.stderr);
    const device = await pickDevice(devices, target);
    if (!device) {
      vscode.window.showWarningMessage('未选择设备，已取消（不会猜测目标）。');
      return;
    }
    layerLog(channel, requestId, 'toolchain', `已选设备 ${device.id} (${device.name})`);

    const built = await runMobileBuild(project, inv, target, configuration, requestId);
    if (!built) {
      return;
    }

    // Stamp check — never install without fresh stamp from this build
    const stampName = target === 'android' ? 'android.ok' : 'ios.ok';
    const stamp = path.join(project.root, '.jnative', 'mobile-staging', stampName);
    if (!fs.existsSync(stamp)) {
      layerLog(channel, requestId, 'install', `缺少构建 stamp ${stamp}，拒绝安装`);
      vscode.window.showErrorMessage('构建未产生有效 stamp，未安装。');
      return;
    }

    layerLog(channel, requestId, 'install', `安装并启动 → ${device.id}`);
    const result = await runCapture(
      inv,
      [
        'mobile',
        'run',
        '--target',
        target,
        '--device',
        device.id,
        '--project',
        project.root,
      ],
      { cwd: project.root, timeoutMs: 300000 }
    );
    channel.appendLine(result.stdout + result.stderr);
    if (result.code !== 0) {
      const cls = classifyMobileFailure(result.stdout + result.stderr);
      layerLog(channel, requestId, cls.layer, cls.hint);
      const msg =
        cls.layer === 'install'
          ? '安装失败'
          : cls.layer === 'launch'
            ? '启动失败'
            : `运行失败（${cls.layer}）`;
      vscode.window.showErrorMessage(`${msg}：${cls.hint}`);
      return;
    }
    setLastMobileSession({
      target,
      deviceId: device.id,
      projectRoot: project.root,
      configuration,
      startedAt: new Date().toISOString(),
      requestId,
    });
    layerLog(channel, requestId, 'launch', '已请求安装并启动；可用「JNative: 停止」结束本次会话');
    vscode.window.showInformationMessage(`已在 ${device.name} 上启动（${configuration}）`);
  });
}

async function runMobileBuild(
  project: JNativeProject,
  inv: ToolchainInvocation,
  target: MobileTarget,
  configuration: MobileConfiguration,
  requestId: string
): Promise<boolean> {
  const channel = getOutput();
  channel.show(true);
  layerLog(
    channel,
    requestId,
    'jnative-compile',
    `mobile build --target ${target} --configuration ${configuration}`
  );
  // Invalidate platform stamps before build so a failed rebuild cannot reuse old ok files.
  try {
    const staging = path.join(project.root, '.jnative', 'mobile-staging');
    for (const name of ['android.ok', 'ios.ok']) {
      const p = path.join(staging, name);
      if (fs.existsSync(p) && (target === 'android' || target === 'ios' || target === 'web')) {
        if (target === 'web' || (target === 'android' && name === 'android.ok') || (target === 'ios' && name === 'ios.ok')) {
          fs.unlinkSync(p);
        }
      }
    }
  } catch {
    /* ignore */
  }

  const result = await vscode.window.withProgress(
    {
      location: vscode.ProgressLocation.Notification,
      title: `JNative 移动构建 (${target}/${configuration})`,
      cancellable: false,
    },
    async (progress) => {
      progress.report({ message: '编译中…' });
      return runCapture(
        inv,
        [
          'mobile',
          'build',
          '--target',
          target,
          '--configuration',
          configuration,
          '--project',
          project.root,
        ],
        { cwd: project.root, timeoutMs: 600000 }
      );
    }
  );
  channel.appendLine(result.stdout);
  if (result.stderr.trim()) {
    channel.appendLine(result.stderr);
  }
  const combined = result.stdout + '\n' + result.stderr;
  const compileLines = extractCompileDiagnostics(combined);
  if (compileLines.length) {
    const textDiags = parseTextDiagnostics(combined);
    applyJsonDiagnostics(project.root, textDiags, project.root);
    for (const line of compileLines) {
      layerLog(channel, requestId, 'jnative-compile', line);
    }
  }
  if (result.code !== 0) {
    const cls = classifyMobileFailure(combined);
    layerLog(channel, requestId, cls.layer, `构建失败 — ${cls.hint}`);
    vscode.window.showErrorMessage(`移动构建失败（${cls.layer}）：${cls.hint}`);
    return false;
  }
  const distWasm = path.join(project.root, 'dist', 'app.wasm');
  const distJs = path.join(project.root, 'dist', 'app.js');
  if (!fs.existsSync(distWasm) || !fs.existsSync(distJs)) {
    layerLog(channel, requestId, 'wasm', '构建退出 0 但缺少 app.wasm/app.js，拒绝继续');
    vscode.window.showErrorMessage('WASM 产物缺失，未安装/预览。');
    return false;
  }
  clearProjectDiagnostics(project.root);
  layerLog(channel, requestId, 'wasm', `产物就绪 dist/app.wasm + app.js (${configuration})`);
  vscode.window.showInformationMessage(`移动构建成功 (${target}/${configuration})`);
  return true;
}

async function packageMobileProject(project: JNativeProject): Promise<void> {
  const inv = await requireToolchain();
  if (!inv) {
    return;
  }
  if (!(await saveDirtySources(project.root))) {
    return;
  }
  const target = await pickMobileTarget();
  if (!target) {
    return;
  }
  const requestId = newRequestId();
  const channel = getOutput();
  channel.show(true);
  layerLog(channel, requestId, 'unknown', '打包：使用 release 配置（不会上传商店、不会写入签名密钥）');
  const ok = await withProjectLock(project.root, () =>
    runMobileBuild(project, inv, target, 'release', requestId)
  );
  if (!ok) {
    return;
  }
  const record = path.join(project.root, '.jnative', 'mobile-build.json');
  channel.appendLine('');
  channel.appendLine('=== 产物清单 ===');
  channel.appendLine(`项目: ${project.root}`);
  channel.appendLine(`目标: ${target}`);
  channel.appendLine(`配置: release`);
  channel.appendLine(`dist/: ${path.join(project.root, 'dist')}`);
  if (fs.existsSync(record)) {
    channel.appendLine(`构建记录: ${record}`);
    try {
      channel.appendLine(fs.readFileSync(record, 'utf8'));
    } catch {
      /* ignore */
    }
  }
  channel.appendLine('限制: store_ready=false；未签名或模拟器产物不具备公开分发条件。');
  channel.appendLine('不会自动提交签名材料或上传商店。');
  vscode.window.showInformationMessage('发布配置产物已生成（见输出面板清单；未上传商店）。');
}
