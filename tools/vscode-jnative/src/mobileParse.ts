/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

// Author: 张晓达
/** Pure helpers (no vscode import) for unit tests and mobileFlow. */

export type ErrorLayer =
  | 'toolchain'
  | 'jnative-compile'
  | 'wasm'
  | 'javascript'
  | 'bridge'
  | 'android-container'
  | 'ios-container'
  | 'install'
  | 'launch'
  | 'unknown';

export interface MobileDevice {
  id: string;
  name: string;
  platform: string;
  state: string;
}

export function parseDevicesJson(stdout: string): MobileDevice[] {
  const text = stdout.trim();
  const start = text.indexOf('[');
  const end = text.lastIndexOf(']');
  if (start < 0 || end < start) {
    return parseDevicesText(stdout);
  }
  try {
    const arr = JSON.parse(text.slice(start, end + 1)) as MobileDevice[];
    if (!Array.isArray(arr)) {
      return [];
    }
    return arr.filter((d) => d && typeof d.id === 'string');
  } catch {
    return parseDevicesText(stdout);
  }
}

export function parseDevicesText(stdout: string): MobileDevice[] {
  const out: MobileDevice[] = [];
  for (const line of stdout.split(/\r?\n/)) {
    const m = line.match(/^\s*([A-Za-z0-9._:-]+)\s{2,}(.+?)\s{2,}\[([^\]]+)\]\s*$/);
    if (!m) {
      continue;
    }
    if (m[1] === 'android' || m[1] === 'ios' || m[1] === '(none)') {
      continue;
    }
    out.push({ id: m[1], name: m[2].trim(), platform: '', state: m[3] });
  }
  return out;
}

export function classifyMobileFailure(output: string): { layer: ErrorLayer; hint: string } {
  const t = output.toLowerCase();
  if (
    /adb not found|android sdk|wat2wasm not found|xcode|simctl|toolchain incomplete|gradlew|not found on path/.test(
      t
    )
  ) {
    return { layer: 'toolchain', hint: '工具链缺失或未就绪，请先运行「JNative: 检查移动工具链」。' };
  }
  if (/wasm compile failed|error:|:\d+:\d+:\s+error:|jnative compile|parse error/.test(t)) {
    return { layer: 'jnative-compile', hint: 'JNative 编译失败；有源位置时已写入 Problems。' };
  }
  if (/app\.wasm|wasm magic|missing wasm|web_build_script failed/.test(t)) {
    return { layer: 'wasm', hint: 'WASM 产物验证失败，未安装旧产物。' };
  }
  if (/protocol_mismatch|capability_unavailable|bridge|timeout/.test(t)) {
    return { layer: 'bridge', hint: '桥接层失败或超时（见关联 requestId）。' };
  }
  if (/adb install|simctl install|install failed|stamped apk missing|no fresh .+ build stamp/.test(t)) {
    return { layer: 'install', hint: '安装失败：请先成功构建；不会使用失败构建的旧 stamp。' };
  }
  if (/am start|simctl launch|launch failed/.test(t) && /fail/.test(t)) {
    return { layer: 'launch', hint: '安装可能成功但启动失败。' };
  }
  if (/android build failed|assemble/.test(t)) {
    return { layer: 'android-container', hint: 'Android 容器/Gradle 构建失败。' };
  }
  if (/ios build failed|xcodebuild/.test(t)) {
    return { layer: 'ios-container', hint: 'iOS 容器/xcodebuild 失败。' };
  }
  return { layer: 'unknown', hint: '见输出面板原始日志（未伪造源文件行号）。' };
}

export function extractCompileDiagnostics(output: string): string[] {
  const lines: string[] = [];
  for (const line of output.split(/\r?\n/)) {
    if (/:\d+:\d+:\s+(error|warning):/.test(line)) {
      lines.push(line);
    }
  }
  return lines;
}

export function redact(text: string): string {
  return text
    .replace(/(authorization|token|password|secret|api[_-]?key)\s*[:=]\s*\S+/gi, '$1=<redacted>')
    .replace(/("(?:token|password|secret|authorization)"\s*:\s*")[^"]+"/gi, '$1<redacted>"');
}
