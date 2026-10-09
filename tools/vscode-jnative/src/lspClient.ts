// Author: 张晓达
import * as vscode from 'vscode';
import { findProjectForFile } from './project';
import { buildArgv, resolveToolchain } from './toolchain';

type LspNode = typeof import('vscode-languageclient/node');
type LanguageClient = import('vscode-languageclient/node').LanguageClient;

let client: LanguageClient | undefined;
let lspApi: LspNode | undefined;

function loadLspApi(): LspNode | undefined {
  if (lspApi) {
    return lspApi;
  }
  try {
    // Lazy require: packaging must include this dependency; missing it must not
    // prevent create/build/run commands from registering.
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    lspApi = require('vscode-languageclient/node') as LspNode;
    return lspApi;
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    vscode.window.showWarningMessage(
      `JNative LSP 不可用（缺少 vscode-languageclient）: ${msg}。创建/构建/运行仍可使用。`
    );
    return undefined;
  }
}

export async function startLanguageServer(context: vscode.ExtensionContext): Promise<void> {
  await stopLanguageServer();
  if (!vscode.workspace.isTrusted) {
    return;
  }
  const cfg = vscode.workspace.getConfiguration('jnative');
  if (!cfg.get<boolean>('enableLanguageServer', true)) {
    return;
  }
  const api = loadLspApi();
  if (!api) {
    return;
  }
  const resolved = await resolveToolchain(vscode.workspace.workspaceFolders);
  if (!resolved.invocation) {
    return;
  }
  const argv = buildArgv(resolved.invocation, ['lsp']);
  const serverOptions: import('vscode-languageclient/node').ServerOptions = {
    command: argv[0],
    args: argv.slice(1),
    options: { shell: false },
  };
  const clientOptions: import('vscode-languageclient/node').LanguageClientOptions = {
    documentSelector: [{ language: 'jnative', scheme: 'file' }],
    synchronize: {
      fileEvents: vscode.workspace.createFileSystemWatcher('**/*.{java,jnative}'),
    },
    middleware: {
      didOpen: async (doc, next) => {
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
  client = new api.LanguageClient('jnative', 'JNative Language Server', serverOptions, clientOptions);
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
      const api = lspApi;
      if (!api || client.state !== api.State.Stopped) {
        await client.stop();
      }
    } catch {
      // ignore
    }
    client = undefined;
  }
}

export function isLspRunning(): boolean {
  const api = lspApi;
  return !!client && !!api && client.state === api.State.Running;
}
