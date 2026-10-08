import * as path from 'path';
import Mocha from 'mocha';
import { readdirSync } from 'fs';

export function run(): Promise<void> {
  const mocha = new Mocha({ ui: 'bdd', color: true, timeout: 60000 });
  const testsRoot = path.resolve(__dirname);
  for (const f of readdirSync(testsRoot)) {
    if (f.endsWith('.test.js')) {
      mocha.addFile(path.join(testsRoot, f));
    }
  }
  return new Promise((resolve, reject) => {
    mocha.run((failures) => {
      if (failures > 0) {
        reject(new Error(`${failures} tests failed`));
      } else {
        resolve();
      }
    });
  });
}
