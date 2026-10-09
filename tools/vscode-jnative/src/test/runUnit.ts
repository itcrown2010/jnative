// Author: 张晓达
import * as path from 'path';
import Mocha from 'mocha';
import { readdirSync } from 'fs';

async function main(): Promise<void> {
  const mocha = new Mocha({ ui: 'bdd', color: true, timeout: 20000 });
  const dir = path.resolve(__dirname);
  for (const f of readdirSync(dir)) {
    if (f.endsWith('.test.js')) {
      mocha.addFile(path.join(dir, f));
    }
  }
  await new Promise<void>((resolve, reject) => {
    mocha.run((failures) => {
      if (failures) {
        reject(new Error(`${failures} tests failed`));
      } else {
        resolve();
      }
    });
  });
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
