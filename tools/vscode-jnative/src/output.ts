/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

// Author: 张晓达
import * as vscode from 'vscode';

let output: vscode.OutputChannel | undefined;

export function getOutput(): vscode.OutputChannel {
  if (!output) {
    output = vscode.window.createOutputChannel('JNative');
  }
  return output;
}

export function disposeOutput(): void {
  output?.dispose();
  output = undefined;
}
