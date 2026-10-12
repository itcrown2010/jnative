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
    const pick = await vscode.window.showQuickPick(
      [
        { label: 'Web 预览', description: 'mobile build --target web + 本地预览', id: 'web' },
        { label: 'Android / iOS', description: '选择设备后安装运行', id: 'device' },
      ],
      { title: '移动项目运行方式' }
    );
    if (!pick) {
      return;
    }
    if (pick.id === 'web') {
      await runMobileWebPreview(project);
      return;
    }
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

/** Explain package artifacts; never auto-publish. */
export async function packageProject(project: JNativeProject): Promise<void> {
  const kind = detectProjectKind(project);
  const channel = getOutput();
  channel.show(true);
  channel.appendLine(`[package] kind=${kind} root=${project.root}`);
  const lines: string[] = [
    'JNative 打包说明（不会自动发布到商店或注册表）：',
    '',
  ];
  if (kind === 'web') {
    lines.push(
      '产物目录: dist/（app.wasm + app.js + index.html）',
      '分发: 可将 dist/ 部署到任意静态主机。',
      '限制: 无应用商店签名；依赖浏览器 WASM 支持。'
    );
  } else if (kind === 'mobile') {
    lines.push(
      'Web: dist/（同上）',
      'Android: 使用 `jnative mobile build --target android` 得到调试 APK（见 CLI 输出路径）。',
      'iOS: 模拟器构建；本阶段无商店签名 / TestFlight。',
      '限制: 移动容器 ≠ 完整原生移动运行时。'
    );
  } else {
    lines.push(
      '产物: 项目构建输出的原生可执行文件（见 build 成功时的 Output 行）。',
      '分发: 自行拷贝二进制及依赖的原生库；不提供自动公证/安装包。'
    );
  }
  for (const l of lines) {
    channel.appendLine(l);
  }
  vscode.window.showInformationMessage('打包说明已写入 JNative 输出面板（未自动发布）。');
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
  // Only stop processes this extension started (native run + web preview).
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
  const target = await vscode.window.showQuickPick(['web', 'android', 'ios'], {
    placeHolder: '选择移动构建目标',
  });
  if (!target) {
    return;
  }
  const channel = getOutput();
  channel.show(true);
  const result = await runCapture(
    inv,
    ['mobile', 'build', '--target', target, '--project', project.root],
    { cwd: project.root, timeoutMs: 600000 }
  );
  channel.appendLine(result.stdout + result.stderr);
  vscode.window.showInformationMessage(result.code === 0 ? `移动构建成功 (${target})` : `移动构建失败 (${target})`);
}

export async function mobileRun(project: JNativeProject): Promise<void> {
  const inv = await requireToolchain();
  if (!inv) {
    return;
  }
  const target = await vscode.window.showQuickPick(['android', 'ios'], {
    placeHolder: '选择运行目标',
  });
  if (!target) {
    return;
  }
  const channel = getOutput();
  channel.show(true);
  const list = await runCapture(inv, ['mobile', 'devices', '--target', target], {
    cwd: project.root,
    timeoutMs: 120000,
  });
  channel.appendLine(list.stdout + list.stderr);
  const device = await vscode.window.showInputBox({
    prompt: '设备 / 模拟器 ID（多设备时必填，见输出面板列表）',
    placeHolder: '例如 emulator-5554 或 UUID',
  });
  if (!device) {
    vscode.window.showWarningMessage('未选择设备，已取消运行（不会猜测目标）。');
    return;
  }
  const result = await runCapture(
    inv,
    ['mobile', 'run', '--target', target, '--device', device, '--project', project.root],
    { cwd: project.root, timeoutMs: 300000 }
  );
  channel.appendLine(result.stdout + result.stderr);
  vscode.window.showInformationMessage(result.code === 0 ? '已请求安装并启动' : '运行失败');
}
