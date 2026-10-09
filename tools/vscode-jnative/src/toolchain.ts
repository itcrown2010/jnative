// Author: 张晓达
import * as fs from 'fs';
import * as path from 'path';
import { spawn } from 'child_process';
import * as vscode from 'vscode';

export interface ToolchainInvocation {
  /** argv[0] */
  command: string;
  /** remaining args before user/subcommand args */
  prefixArgs: string[];
  /** human description of how toolchain was resolved */
  source: string;
}

export interface ToolchainCheckResult {
  ok: boolean;
  lines: string[];
  invocation?: ToolchainInvocation;
}

/**
 * Resolve JNative CLI launch command.
 * Priority: executablePath → jarPath+javaPath → PATH `jnative` → repo scripts/jnative.sh
 */
export async function resolveToolchain(
  workspaceFolders: readonly vscode.WorkspaceFolder[] | undefined
): Promise<{ invocation?: ToolchainInvocation; missing?: string }> {
  const cfg = vscode.workspace.getConfiguration('jnative');
  const execPath = (cfg.get<string>('executablePath') || '').trim();
  const jarPath = (cfg.get<string>('jarPath') || '').trim();
  const javaPath = (cfg.get<string>('javaPath') || 'java').trim() || 'java';

  if (execPath) {
    if (!fs.existsSync(execPath)) {
      return { missing: `jnative.executablePath 指向的文件不存在: ${execPath}` };
    }
    return {
      invocation: { command: execPath, prefixArgs: [], source: 'jnative.executablePath' },
    };
  }

  if (jarPath) {
    if (!fs.existsSync(jarPath)) {
      return { missing: `jnative.jarPath 指向的文件不存在: ${jarPath}` };
    }
    return {
      invocation: {
        command: javaPath,
        prefixArgs: ['-jar', jarPath],
        source: 'jnative.jarPath + jnative.javaPath',
      },
    };
  }

  const onPath = await findOnPath('jnative');
  if (onPath) {
    return {
      invocation: { command: onPath, prefixArgs: [], source: 'PATH jnative' },
    };
  }

  // Development repo: walk up from workspace folders looking for scripts/jnative.sh
  const roots = workspaceFolders?.map((f) => f.uri.fsPath) ?? [];
  for (const root of roots) {
    const script = findRepoScript(root);
    if (script) {
      return {
        invocation: {
          command: script,
          prefixArgs: [],
          source: `repository ${path.relative(root, script) || script}`,
        },
      };
    }
  }

  // Also check extension's sibling repo when developing the extension itself
  const extRepo = path.resolve(__dirname, '..', '..', '..');
  const script = path.join(extRepo, 'scripts', 'jnative.sh');
  if (fs.existsSync(script)) {
    return {
      invocation: { command: script, prefixArgs: [], source: 'tools/../scripts/jnative.sh' },
    };
  }

  return {
    missing:
      '未找到 JNative 工具链。请设置 jnative.executablePath 或 jnative.jarPath，或将 jnative 加入 PATH。',
  };
}

function findRepoScript(start: string): string | undefined {
  let cur = path.resolve(start);
  for (let i = 0; i < 8; i++) {
    const candidate = path.join(cur, 'scripts', 'jnative.sh');
    if (fs.existsSync(candidate)) {
      return candidate;
    }
    const parent = path.dirname(cur);
    if (parent === cur) {
      break;
    }
    cur = parent;
  }
  return undefined;
}

export async function findOnPath(bin: string): Promise<string | undefined> {
  const pathEnv = process.env.PATH || '';
  const sep = process.platform === 'win32' ? ';' : ':';
  for (const dir of pathEnv.split(sep)) {
    if (!dir) {
      continue;
    }
    const full = path.join(dir, bin);
    if (fs.existsSync(full)) {
      return full;
    }
  }
  return undefined;
}

export function buildArgv(inv: ToolchainInvocation, args: string[]): string[] {
  return [inv.command, ...inv.prefixArgs, ...args];
}

/** Spawn without shell; argv are separate parameters. */
export function spawnToolchain(
  inv: ToolchainInvocation,
  args: string[],
  opts: { cwd?: string; env?: NodeJS.ProcessEnv }
): ReturnType<typeof spawn> {
  const argv = buildArgv(inv, args);
  return spawn(argv[0], argv.slice(1), {
    cwd: opts.cwd,
    env: opts.env ?? process.env,
    shell: false,
  });
}

export async function runCapture(
  inv: ToolchainInvocation,
  args: string[],
  opts: { cwd?: string; timeoutMs?: number } = {}
): Promise<{ code: number; stdout: string; stderr: string; timedOut?: boolean }> {
  return new Promise((resolve) => {
    const child = spawnToolchain(inv, args, { cwd: opts.cwd });
    let stdout = '';
    let stderr = '';
    let timedOut = false;
    const timer =
      opts.timeoutMs && opts.timeoutMs > 0
        ? setTimeout(() => {
            timedOut = true;
            child.kill('SIGTERM');
            setTimeout(() => child.kill('SIGKILL'), 2000);
          }, opts.timeoutMs)
        : undefined;
    child.stdout?.on('data', (d: Buffer) => {
      stdout += d.toString('utf8');
    });
    child.stderr?.on('data', (d: Buffer) => {
      stderr += d.toString('utf8');
    });
    child.on('error', (err) => {
      if (timer) {
        clearTimeout(timer);
      }
      resolve({ code: 1, stdout, stderr: stderr + '\n' + err.message, timedOut });
    });
    child.on('close', (code) => {
      if (timer) {
        clearTimeout(timer);
      }
      resolve({ code: code ?? 1, stdout, stderr, timedOut });
    });
  });
}

export async function checkToolchain(
  workspaceFolders: readonly vscode.WorkspaceFolder[] | undefined
): Promise<ToolchainCheckResult> {
  const lines: string[] = [];
  const resolved = await resolveToolchain(workspaceFolders);
  if (!resolved.invocation) {
    lines.push('✗ JNative CLI: ' + (resolved.missing || '未配置'));
    return { ok: false, lines };
  }
  const inv = resolved.invocation;
  lines.push(`✓ JNative 入口: ${inv.command} ${inv.prefixArgs.join(' ')}`.trim());
  lines.push(`  来源: ${inv.source}`);

  const ver = await runCapture(inv, ['--version'], { timeoutMs: 20000 });
  if (ver.code === 0 && (ver.stdout + ver.stderr).toLowerCase().includes('jnative')) {
    lines.push(`✓ jnative --version: ${(ver.stdout || ver.stderr).trim()}`);
  } else {
    lines.push(`✗ jnative --version 失败 (exit ${ver.code}): ${(ver.stderr || ver.stdout).trim()}`);
    return { ok: false, lines, invocation: inv };
  }

  const doctor = await runCapture(inv, ['doctor'], { timeoutMs: 60000 });
  lines.push('--- jnative doctor ---');
  lines.push((doctor.stdout || doctor.stderr).trim() || `(exit ${doctor.code})`);

  const javaCfg = vscode.workspace.getConfiguration('jnative').get<string>('javaPath') || 'java';
  const javaCheck = await runCapture(
    { command: javaCfg, prefixArgs: [], source: 'java' },
    ['-version'],
    { timeoutMs: 15000 }
  );
  // java -version writes to stderr
  if (javaCheck.code === 0 || (javaCheck.stderr + javaCheck.stdout).includes('version')) {
    lines.push(`✓ Java (${javaCfg}): ${(javaCheck.stderr || javaCheck.stdout).split('\n')[0]}`);
  } else {
    lines.push(`✗ Java 不可用 (${javaCfg})`);
  }

  const clang = await findOnPath('clang');
  if (clang) {
    const c = await runCapture({ command: clang, prefixArgs: [], source: 'clang' }, ['--version'], {
      timeoutMs: 15000,
    });
    lines.push(`✓ clang: ${(c.stdout || '').split('\n')[0] || clang}`);
  } else {
    lines.push('✗ clang 未在 PATH 中（原生链接通常需要）');
  }

  const ok =
    ver.code === 0 &&
    (javaCheck.code === 0 || (javaCheck.stderr + javaCheck.stdout).includes('version'));
  return { ok, lines, invocation: inv };
}

export async function promptMissingToolchain(missing: string): Promise<void> {
  const choice = await vscode.window.showErrorMessage(missing, '打开设置');
  if (choice === '打开设置') {
    await vscode.commands.executeCommand('workbench.action.openSettings', 'jnative.');
  }
}
