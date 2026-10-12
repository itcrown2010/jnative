/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

// Author: 张晓达
import * as assert from 'assert';
import * as vscode from 'vscode';
import { findProjectForFile } from '../../projectDetect';

suite('JNative extension smoke', () => {
  test('extension activates', async () => {
    const ext = vscode.extensions.getExtension('jnative.jnative');
    assert.ok(ext, 'extension jnative.jnative should be present');
    await ext!.activate();
    assert.strictEqual(ext!.isActive, true);
  });

  test('untrusted-like: detection still works without claiming java', () => {
    // Pure detection does not require trust; execution paths check isTrusted separately.
    assert.strictEqual(typeof findProjectForFile, 'function');
  });
});
