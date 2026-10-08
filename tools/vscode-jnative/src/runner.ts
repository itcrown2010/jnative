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
import { JNativeProject } from './project';
import {
  promptMissingToolchain,
  resolveToolchain,
  runCapture,
  spawnToolchain,
  ToolchainInvocation,
} from './toolchain';

const buildLocks = new Map<string, Promise<unknown>>();
let runProcess: ChildProcess | undefined;
let output: vscode.OutputChannel | undefined;

export function getOutput(): vscode.OutputChannel {
  if (!output) {
    output = vscode.window.createOutputChannel('JNative');
  }
  return output;
}

export function disposeRunner(): void {
  stopRun();
  output?.dispose();
  output = undefined;
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

export interface BuildResult {
  success: boolean;
  artifact?: string;
}

export async function buildProject(project: JNativeProject, debug = false): Promise<BuildResult> {
  const inv = await requireToolchain();
  if (!inv) {
    return { success: false };
  }
  return withProjectLock(project.root, async () => {
    const channel = getOutput();
    channel.show(true);
    channel.appendLine(`[build${debug ? ' --debug' : ''}] ${project.root}`);
    const cfg = vscode.workspace.getConfiguration('jnative');
    const extra = cfg.get<string[]>('buildArguments') ?? [];
    const args = [
      'build',
      '--project',
      project.root,
      '--format',
      'json',
      ...(debug ? ['--debug'] : []),
      ...extra,
    ];
    const result = await runCapture(inv, args, { cwd: project.root, timeoutMs: 300000 });
    channel.appendLine(result.stdout);
    if (result.stderr.trim()) {
      channel.appendLine(result.stderr);
    }
    const report = parseBuildReport(result.stdout);
    if (report) {
      if (report.success) {
        clearProjectDiagnostics(project.root);
      } else {
        applyJsonDiagnostics(project.root, report.diagnostics, project.root);
      }
      if (report.success && report.artifact && fs.existsSync(report.artifact)) {
        channel.appendLine(`[artifact] ${report.artifact}`);
        return { success: true, artifact: report.artifact };
      }
      if (report.success) {
        channel.appendLine('[build] success but artifact missing');
        return { success: false };
      }
      return { success: false };
    }
    // Fallback: text diagnostics from stderr/stdout
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
      return { success: false };
    }
    applyJsonDiagnostics(project.root, textDiags, project.root);
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
  const outDir = path.join(path.dirname(filePath), '.jnative-file-build');
  fs.mkdirSync(outDir, { recursive: true });
  const base = path.basename(filePath, '.java');
  const outFile = path.join(outDir, base);
  channel.appendLine(`[build-file] ${filePath}`);
  const result = await runCapture(
    inv,
    [filePath, '-o', outFile, '--format', 'json'],
    { cwd: path.dirname(filePath), timeoutMs: 300000 }
  );
  channel.appendLine(result.stdout);
  if (result.stderr.trim()) {
    channel.appendLine(result.stderr);
  }
  const projectRoot = path.dirname(filePath);
  const report = parseBuildReport(result.stdout);
  if (report) {
    if (report.success) {
      clearProjectDiagnostics(projectRoot);
      const art = report.artifact && fs.existsSync(report.artifact) ? report.artifact : outFile;
      return { success: true, artifact: art };
    }
    applyJsonDiagnostics(projectRoot, report.diagnostics, projectRoot);
    return { success: false };
  }
  if (result.code === 0 && fs.existsSync(outFile)) {
    clearProjectDiagnostics(projectRoot);
    return { success: true, artifact: outFile };
  }
  applyJsonDiagnostics(projectRoot, parseTextDiagnostics(result.stdout + '\n' + result.stderr), projectRoot);
  return { success: false };
}

export async function runProject(project: JNativeProject): Promise<void> {
  const built = await buildProject(project, false);
  if (!built.success || !built.artifact) {
    vscode.window.showErrorMessage('构建失败，未启动运行（不会使用旧产物）。');
    return;
  }
  await runArtifact(built.artifact, project.root);
}

/** Build with --debug then launch CodeLLDB (vadimcn.vscode-lldb). */
export async function debugProject(project: JNativeProject): Promise<void> {
  if (!vscode.workspace.isTrusted) {
    vscode.window.showWarningMessage('工作区未受信任：禁止调试。');
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
}

export async function createProject(): Promise<void> {
  if (!vscode.workspace.isTrusted) {
    vscode.window.showWarningMessage('工作区未受信任：禁止创建并调用工具链。');
    return;
  }
  const inv = await requireToolchain();
  if (!inv) {
    return;
  }
  const folderUri = await vscode.window.showOpenDialog({
    canSelectFiles: false,
    canSelectFolders: true,
    canSelectMany: false,
    openLabel: '选择父目录',
  });
  if (!folderUri?.length) {
    return;
  }
  const parent = folderUri[0].fsPath;
  const name = await vscode.window.showInputBox({
    prompt: '项目名称',
    value: 'hello-jnative',
    validateInput: (v) => (/^[A-Za-z_][A-Za-z0-9_-]*$/.test(v) ? undefined : '非法项目名'),
  });
  if (!name) {
    return;
  }
  const target = path.join(parent, name);
  if (fs.existsSync(path.join(target, 'jnative.project')) || fs.existsSync(path.join(target, 'src', 'Main.java'))) {
    vscode.window.showErrorMessage(`拒绝覆盖已有文件: ${target}`);
    return;
  }
  const channel = getOutput();
  channel.show(true);
  const result = await runCapture(inv, ['new', name, '--dir', parent, '--minimal'], {
    cwd: parent,
    timeoutMs: 60000,
  });
  channel.appendLine(result.stdout + result.stderr);
  if (result.code !== 0) {
    vscode.window.showErrorMessage('创建项目失败，见 JNative 输出。');
    return;
  }
  const open = await vscode.window.showInformationMessage(`已创建 ${target}`, '打开项目');
  if (open === '打开项目') {
    await vscode.commands.executeCommand('vscode.openFolder', vscode.Uri.file(target), true);
  }
}

export async function createMobileProject(): Promise<void> {
  if (!vscode.workspace.isTrusted) {
    vscode.window.showWarningMessage('工作区未受信任：禁止创建并调用工具链。');
    return;
  }
  const inv = await requireToolchain();
  if (!inv) {
    return;
  }
  const folderUri = await vscode.window.showOpenDialog({
    canSelectFiles: false,
    canSelectFolders: true,
    canSelectMany: false,
    openLabel: '选择父目录',
  });
  if (!folderUri?.length) {
    return;
  }
  const parent = folderUri[0].fsPath;
  const name = await vscode.window.showInputBox({
    prompt: '移动项目名称',
    value: 'my-mobile-app',
    validateInput: (v) => (/^[A-Za-z_][A-Za-z0-9_-]*$/.test(v) ? undefined : '非法项目名'),
  });
  if (!name) {
    return;
  }
  const target = path.join(parent, name);
  if (fs.existsSync(path.join(target, 'jnative.toml')) || fs.existsSync(path.join(target, 'src', 'App.java'))) {
    vscode.window.showErrorMessage(`拒绝覆盖已有文件: ${target}`);
    return;
  }
  const channel = getOutput();
  channel.show(true);
  const result = await runCapture(inv, ['new', name, '--template', 'mobile', '--dir', parent], {
    cwd: parent,
    timeoutMs: 120000,
  });
  channel.appendLine(result.stdout + result.stderr);
  if (result.code !== 0) {
    vscode.window.showErrorMessage('创建移动项目失败，见 JNative 输出。');
    return;
  }
  const open = await vscode.window.showInformationMessage(`已创建移动项目 ${target}`, '打开项目');
  if (open === '打开项目') {
    await vscode.commands.executeCommand('vscode.openFolder', vscode.Uri.file(target), true);
  }
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
