/**
 * Optional VS Code integration host.
 * Requires VS Code installed; skipped gracefully when unavailable.
 */
import * as path from 'path';
import { runTests } from '@vscode/test-electron';

async function main(): Promise<void> {
  const extensionDevelopmentPath = path.resolve(__dirname, '../../');
  const extensionTestsPath = path.resolve(__dirname, './suite/index');
  const code =
    process.env.VSCODE_EXECUTABLE ||
    '/Applications/Visual Studio Code.app/Contents/Resources/app/bin/code';
  try {
    await runTests({
      extensionDevelopmentPath,
      extensionTestsPath,
      vscodeExecutablePath: undefined,
      launchArgs: ['--disable-extensions'],
    });
  } catch (err) {
    console.error('VS Code integration tests failed or VS Code unavailable:', err);
    console.error(`Hint: set VSCODE_EXECUTABLE (tried default code at ${code})`);
    process.exit(1);
  }
}

main();
