// Author: 张晓达
import * as assert from 'assert';
import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';
import {
  discoverProjectsUnder,
  fileBelongsToProject,
  findProjectForFile,
  isProjectRoot,
  parseBuildReportJson,
  toVsCodePosition,
} from '../projectDetect';

function mkTmp(): string {
  return fs.mkdtempSync(path.join(os.tmpdir(), 'jnative-ext-'));
}

describe('projectDetect', () => {
  it('does not treat random java as jnative without project markers', () => {
    const root = mkTmp();
    fs.mkdirSync(path.join(root, 'src'));
    fs.writeFileSync(path.join(root, 'src', 'App.java'), 'class App {}');
    assert.strictEqual(isProjectRoot(root), false);
    assert.strictEqual(findProjectForFile(path.join(root, 'src', 'App.java')), undefined);
  });

  it('recognizes jnative.project source roots only', () => {
    const root = mkTmp();
    fs.mkdirSync(path.join(root, 'src'), { recursive: true });
    fs.mkdirSync(path.join(root, 'other'), { recursive: true });
    fs.writeFileSync(
      path.join(root, 'jnative.project'),
      JSON.stringify({ name: 'demo', source: ['src'], main: 'Main', output: 'build/demo' })
    );
    fs.writeFileSync(path.join(root, 'src', 'Main.java'), 'class Main {}');
    fs.writeFileSync(path.join(root, 'other', 'X.java'), 'class X {}');
    assert.ok(isProjectRoot(root));
    assert.ok(findProjectForFile(path.join(root, 'src', 'Main.java')));
    assert.strictEqual(findProjectForFile(path.join(root, 'other', 'X.java')), undefined);
  });

  it('toml alone without src is not a project root', () => {
    const root = mkTmp();
    fs.writeFileSync(path.join(root, 'jnative.toml'), '[package]\nname="x"\n');
    assert.strictEqual(isProjectRoot(root), false);
  });

  it('multi-root discovery finds separate projects', () => {
    const base = mkTmp();
    const a = path.join(base, 'a');
    const b = path.join(base, 'b');
    for (const p of [a, b]) {
      fs.mkdirSync(path.join(p, 'src'), { recursive: true });
      fs.writeFileSync(
        path.join(p, 'jnative.project'),
        JSON.stringify({ name: path.basename(p), source: ['src'], main: 'Main', output: 'build/x' })
      );
      fs.writeFileSync(path.join(p, 'src', 'Main.java'), 'class Main {}');
    }
    const found = discoverProjectsUnder([base]);
    assert.strictEqual(found.length, 2);
  });

  it('parent jnative.toml does not steal nested java outside source roots', () => {
    const root = mkTmp();
    fs.mkdirSync(path.join(root, 'src'), { recursive: true });
    fs.mkdirSync(path.join(root, 'vendor'), { recursive: true });
    fs.writeFileSync(path.join(root, 'jnative.toml'), '[package]\nname="app"\nnamespace="local"\nversion="0.1.0"\nlicense="MIT"\n');
    fs.writeFileSync(
      path.join(root, 'jnative.project'),
      JSON.stringify({ name: 'app', source: ['src'], main: 'Main', output: 'build/app' })
    );
    fs.writeFileSync(path.join(root, 'src', 'Main.java'), 'class Main {}');
    fs.writeFileSync(path.join(root, 'vendor', 'Lib.java'), 'class Lib {}');
    const proj = findProjectForFile(path.join(root, 'src', 'Main.java'));
    assert.ok(proj);
    assert.strictEqual(fileBelongsToProject(path.join(root, 'vendor', 'Lib.java'), proj!), false);
    assert.strictEqual(findProjectForFile(path.join(root, 'vendor', 'Lib.java')), undefined);
  });

  it('maps 1-based diagnostics to 0-based positions', () => {
    assert.deepStrictEqual(toVsCodePosition(1, 1), { line: 0, character: 0 });
    assert.deepStrictEqual(toVsCodePosition(3, 5), { line: 2, character: 4 });
  });

  it('parses JSON build report and ignores log lines', () => {
    const stdout = [
      '[pkg] resolved 0 packages, source roots=0',
      'Build succeeded in 0.10s',
      '{"success":true,"artifact":"/tmp/app","diagnostics":[]}',
    ].join('\n');
    const report = parseBuildReportJson(stdout);
    assert.ok(report);
    assert.strictEqual(report!.success, true);
    assert.strictEqual(report!.artifact, '/tmp/app');
  });

  it('failed build report does not look like success', () => {
    const report = parseBuildReportJson(
      '{"success":false,"artifact":null,"diagnostics":[{"file":"Main.java","line":2,"column":1,"severity":"error","message":"x"}]}'
    );
    assert.ok(report);
    assert.strictEqual(report!.success, false);
    assert.strictEqual(report!.diagnostics.length, 1);
  });
});
