/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

// Author: 张晓达
import * as vscode from 'vscode';
import {
  classifyMobileFailure,
  extractCompileDiagnostics,
  parseDevicesJson,
  parseDevicesText,
  redact,
  type ErrorLayer,
  type MobileDevice,
} from './mobileParse';

export type { ErrorLayer, MobileDevice };
export {
  classifyMobileFailure,
  extractCompileDiagnostics,
  parseDevicesJson,
  parseDevicesText,
};

export type MobileTarget = 'web' | 'android' | 'ios';
export type MobileConfiguration = 'debug' | 'release';

export interface MobileSession {
  target: 'android' | 'ios';
  deviceId: string;
  projectRoot: string;
  configuration: MobileConfiguration;
  startedAt: string;
  requestId: string;
}

let lastSession: MobileSession | undefined;

export function getLastMobileSession(): MobileSession | undefined {
  return lastSession;
}

export function setLastMobileSession(session: MobileSession | undefined): void {
  lastSession = session;
}

export function newRequestId(): string {
  return `m-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

export function layerLog(
  channel: vscode.OutputChannel,
  requestId: string,
  layer: ErrorLayer,
  message: string
): void {
  channel.appendLine(`[${requestId}] [${layer}] ${redact(message)}`);
}

export async function pickMobileTarget(): Promise<MobileTarget | undefined> {
  const pick = await vscode.window.showQuickPick(
    [
      { label: 'Web', description: '构建 WASM + 本地预览', id: 'web' as const },
      { label: 'Android', description: '检查工具链 → 构建设备 → 安装启动', id: 'android' as const },
      { label: 'iOS', description: '检查工具链 → 构建模拟器 → 安装启动', id: 'ios' as const },
    ],
    { title: '选择运行目标', placeHolder: 'Web / Android / iOS' }
  );
  return pick?.id;
}

export async function pickConfiguration(): Promise<MobileConfiguration | undefined> {
  const pick = await vscode.window.showQuickPick(
    [
      {
        label: '调试 (debug)',
        description: '日志 / WebView 检查 / 开发配置',
        id: 'debug' as const,
      },
      {
        label: '发布 (release)',
        description: '内置资源、关闭开发入口；未签名 ≠ 商店分发',
        id: 'release' as const,
      },
    ],
    { title: '构建配置', placeHolder: 'debug 或 release' }
  );
  return pick?.id;
}

export async function pickDevice(
  devices: MobileDevice[],
  target: 'android' | 'ios'
): Promise<MobileDevice | undefined> {
  if (devices.length === 0) {
    vscode.window.showErrorMessage(
      target === 'android'
        ? '未找到可用 Android 设备/模拟器。请启动模拟器或连接设备后重试。'
        : '未找到可用 iOS 模拟器。请安装 Simulator runtime 后重试。'
    );
    return undefined;
  }
  if (devices.length === 1) {
    const only = devices[0];
    const ok = await vscode.window.showQuickPick(
      [
        { label: `使用 ${only.name}`, description: `${only.id} [${only.state}]`, id: 'use' },
        { label: '取消', id: 'cancel' },
      ],
      { title: '确认唯一设备（不会在多设备时猜测）' }
    );
    return ok?.id === 'use' ? only : undefined;
  }
  const pick = await vscode.window.showQuickPick(
    devices.map((d) => ({
      label: d.name,
      description: `${d.id} [${d.state}]`,
      detail: target,
      device: d,
    })),
    {
      title: `选择 ${target} 设备（共 ${devices.length} 台，必须明确选择）`,
      placeHolder: '不会自动猜测目标设备',
    }
  );
  return pick?.device;
}
