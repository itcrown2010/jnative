import * as vscode from 'vscode';
import {
  LanguageClient,
  LanguageClientOptions,
  ServerOptions,
  State,
} from 'vscode-languageclient/node';
import { findProjectForFile } from './project';
import { buildArgv, resolveToolchain } from './toolchain';

let client: LanguageClient | undefined;

export async function startLanguageServer(context: vscode.ExtensionContext): Promise<void> {
  await stopLanguageServer();
  if (!vscode.workspace.isTrusted) {
    return;
  }
  const cfg = vscode.workspace.getConfiguration('jnative');
  if (!cfg.get<boolean>('enableLanguageServer', true)) {
    return;
  }
  const resolved = await resolveToolchain(vscode.workspace.workspaceFolders);
  if (!resolved.invocation) {
    return;
  }
  const argv = buildArgv(resolved.invocation, ['lsp']);
  const serverOptions: ServerOptions = {
    command: argv[0],
    args: argv.slice(1),
    options: { shell: false },
  };
  const clientOptions: LanguageClientOptions = {
    documentSelector: [{ language: 'jnative', scheme: 'file' }],
    synchronize: {
      fileEvents: vscode.workspace.createFileSystemWatcher('**/*.{java,jnative}'),
    },
    middleware: {
      didOpen: async (doc, next) => {
        // Only forward docs that belong to a JNative project (or manually set language).
        if (doc.languageId !== 'jnative') {
          return;
        }
        const proj = findProjectForFile(doc.uri.fsPath);
        if (!proj && doc.languageId !== 'jnative') {
          return;
        }
        return next(doc);
      },
    },
  };
  client = new LanguageClient('jnative', 'JNative Language Server', serverOptions, clientOptions);
  context.subscriptions.push(client);
  try {
    await client.start();
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    vscode.window.showWarningMessage(`JNative LSP 启动失败: ${msg}`);
    client = undefined;
  }
}

export async function stopLanguageServer(): Promise<void> {
  if (client) {
    try {
      if (client.state !== State.Stopped) {
        await client.stop();
      }
    } catch {
      // ignore
    }
    client = undefined;
  }
}

export function isLspRunning(): boolean {
  return !!client && client.state === State.Running;
}
