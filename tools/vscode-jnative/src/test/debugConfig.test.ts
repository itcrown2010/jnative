// Author: 张晓达
import * as assert from 'assert';
import * as path from 'path';
import { buildLaunchConfig, shouldRefuseStaleArtifact } from '../launchConfig';
import { JNativeProject } from '../project';

describe('launchConfig', () => {
  it('launch config uses CodeLLDB type and absolute program', () => {
    const project = {
      root: '/tmp/my project/app',
      name: 'app',
      sourceRoots: ['src'],
      mainClass: 'Main',
      hasProjectFile: true,
      hasToml: false,
    } as JNativeProject;
    const artifact = path.join(project.root, 'build', 'App');
    const cfg = buildLaunchConfig(project, artifact, ['--flag'], { FOO: 'bar' });
    assert.strictEqual(cfg.type, 'lldb');
    assert.strictEqual(cfg.request, 'launch');
    assert.strictEqual(cfg.program, artifact);
    assert.strictEqual(cfg.cwd, project.root);
    assert.deepStrictEqual(cfg.args, ['--flag']);
    assert.deepStrictEqual(cfg.env, { FOO: 'bar' });
    assert.ok(cfg.sourceLanguages.includes('jnative'));
  });

  it('paths with spaces are preserved as single argv fields', () => {
    const project = {
      root: '/tmp/含 空格/proj',
      name: 'proj',
      sourceRoots: ['src'],
      mainClass: 'Main',
      hasProjectFile: true,
      hasToml: false,
    } as JNativeProject;
    const artifact = '/tmp/含 空格/proj/build/Out Bin';
    const cfg = buildLaunchConfig(project, artifact, [], {});
    assert.strictEqual(cfg.program, artifact);
    assert.strictEqual(cfg.cwd, project.root);
  });

  it('failed or missing artifact refuses stale launch', () => {
    assert.strictEqual(shouldRefuseStaleArtifact(false, '/old/bin', () => true), true);
    assert.strictEqual(shouldRefuseStaleArtifact(true, undefined, () => true), true);
    assert.strictEqual(shouldRefuseStaleArtifact(true, '/missing', () => false), true);
    assert.strictEqual(shouldRefuseStaleArtifact(true, '/ok', () => true), false);
  });
});
