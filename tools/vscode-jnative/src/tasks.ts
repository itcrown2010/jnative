import * as path from 'path';
import * as vscode from 'vscode';
import { discoverWorkspaceProjects, pickProject } from './project';
import { buildArgv, resolveToolchain } from './toolchain';

interface JNativeTaskDefinition extends vscode.TaskDefinition {
  task: 'build' | 'run' | 'buildFile';
  project?: string;
  file?: string;
}

export class JNativeTaskProvider implements vscode.TaskProvider {
  static readonly type = 'jnative';

  provideTasks(): vscode.ProviderResult<vscode.Task[]> {
    const projects = discoverWorkspaceProjects(vscode.workspace.workspaceFolders);
    const tasks: vscode.Task[] = [];
    for (const p of projects) {
      tasks.push(this.makeTask('build', p.root, `JNative: 构建 ${p.name}`));
      tasks.push(this.makeTask('run', p.root, `JNative: 运行 ${p.name}`));
    }
    return tasks;
  }

  async resolveTask(task: vscode.Task): Promise<vscode.Task | undefined> {
    const def = task.definition as JNativeTaskDefinition;
    if (def.type !== JNativeTaskProvider.type) {
      return undefined;
    }
    return this.makeTask(def.task, def.project, task.name, def.file);
  }

  private makeTask(
    kind: 'build' | 'run' | 'buildFile',
    projectRoot: string | undefined,
    name: string,
    file?: string
  ): vscode.Task {
    const def: JNativeTaskDefinition = {
      type: JNativeTaskProvider.type,
      task: kind,
      project: projectRoot,
      file,
    };
    const execution = new vscode.CustomExecution(async () => {
      return new JNativeBuildPseudoterminal(kind, projectRoot, file);
    });
    const task = new vscode.Task(
      def,
      vscode.TaskScope.Workspace,
      name,
      'jnative',
      execution,
      ['$jnative']
    );
    task.group = kind === 'build' || kind === 'buildFile' ? vscode.TaskGroup.Build : undefined;
    return task;
  }
}

class JNativeBuildPseudoterminal implements vscode.Pseudoterminal {
  private writeEmitter = new vscode.EventEmitter<string>();
  private closeEmitter = new vscode.EventEmitter<number>();
  onDidWrite = this.writeEmitter.event;
  onDidClose = this.closeEmitter.event;

  constructor(
    private readonly kind: 'build' | 'run' | 'buildFile',
    private readonly projectRoot: string | undefined,
    private readonly file?: string
  ) {}

  open(): void {
    void this.run();
  }

  close(): void {
    // no-op
  }

  private async run(): Promise<void> {
    if (!vscode.workspace.isTrusted) {
      this.writeEmitter.fire('工作区未受信任，拒绝执行。\r\n');
      this.closeEmitter.fire(1);
      return;
    }
    const resolved = await resolveToolchain(vscode.workspace.workspaceFolders);
    if (!resolved.invocation) {
      this.writeEmitter.fire((resolved.missing || '工具链未配置') + '\r\n');
      this.closeEmitter.fire(1);
      return;
    }
    const { spawn } = await import('child_process');
    let args: string[] = [];
    let cwd = this.projectRoot || process.cwd();
    if (this.kind === 'build') {
      const root = this.projectRoot || (await pickProject(vscode.workspace.workspaceFolders))?.root;
      if (!root) {
        this.closeEmitter.fire(1);
        return;
      }
      cwd = root;
      const extra = vscode.workspace.getConfiguration('jnative').get<string[]>('buildArguments') ?? [];
      args = ['build', '--project', root, '--format', 'json', ...extra];
    } else if (this.kind === 'run') {
      // Delegate to extension command for correct "build then run / no stale binary" behavior
      await vscode.commands.executeCommand('jnative.runProject');
      this.closeEmitter.fire(0);
      return;
    } else {
      const file = this.file || vscode.window.activeTextEditor?.document.uri.fsPath;
      if (!file) {
        this.writeEmitter.fire('无当前文件\r\n');
        this.closeEmitter.fire(1);
        return;
      }
      cwd = path.dirname(file);
      const out = path.join(cwd, '.jnative-file-build', path.basename(file, '.java'));
      args = [file, '-o', out, '--format', 'json'];
    }
    const argv = buildArgv(resolved.invocation, args);
    this.writeEmitter.fire(`> ${argv.map(shellEscape).join(' ')}\r\n`);
    const child = spawn(argv[0], argv.slice(1), { cwd, shell: false });
    child.stdout?.on('data', (d: Buffer) => this.writeEmitter.fire(d.toString('utf8').replace(/\n/g, '\r\n')));
    child.stderr?.on('data', (d: Buffer) => this.writeEmitter.fire(d.toString('utf8').replace(/\n/g, '\r\n')));
    child.on('close', (code) => this.closeEmitter.fire(code ?? 1));
  }
}

function shellEscape(s: string): string {
  if (/[\s"']/.test(s)) {
    return `"${s.replace(/"/g, '\\"')}"`;
  }
  return s;
}

export function registerTaskProvider(context: vscode.ExtensionContext): void {
  context.subscriptions.push(
    vscode.tasks.registerTaskProvider(JNativeTaskProvider.type, new JNativeTaskProvider())
  );
}
