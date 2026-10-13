/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

// Author: 张晓达
import * as assert from 'assert';
import {
  classifyMobileFailure,
  parseDevicesJson,
  parseDevicesText,
} from '../mobileParse';

describe('mobileParse', () => {
  it('parses devices json', () => {
    const raw = 'ios devices:\n[{"id":"ABC","name":"iPhone 17","platform":"ios","state":"Booted"}]';
    const list = parseDevicesJson(raw);
    assert.strictEqual(list.length, 1);
    assert.strictEqual(list[0].id, 'ABC');
  });

  it('parses devices text', () => {
    const raw = 'android devices:\n  emulator-5554  sdk gphone  [device]\n';
    const list = parseDevicesText(raw);
    assert.strictEqual(list.length, 1);
    assert.strictEqual(list[0].id, 'emulator-5554');
  });

  it('classifies toolchain vs install', () => {
    assert.strictEqual(classifyMobileFailure('adb not found').layer, 'toolchain');
    assert.strictEqual(classifyMobileFailure('adb install failed').layer, 'install');
    assert.strictEqual(
      classifyMobileFailure('wasm compile failed:\nfoo.java:1:1: error: x').layer,
      'jnative-compile'
    );
  });
});
