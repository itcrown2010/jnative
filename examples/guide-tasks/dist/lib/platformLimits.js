// Author: 张晓达
/** Shared file / share limits for Web and bridge hosts (keep in sync with native). */

export const FILE_MAX_BYTES = 1_048_576; // 1 MiB — backups stay bounded; not streamed into WASM
export const FILE_MAX_NAME = 180;
export const FILE_MAX_MIME = 128;
export const FILE_PICK_TIMEOUT_MS = 300_000;
export const FILE_IO_TIMEOUT_MS = 60_000;

export const CAPABILITY_NAMES = [
  'platform.getName',
  'platform.queryCapabilities',
  'storage.get',
  'storage.set',
  'storage.remove',
  'lifecycle',
  'file.pick',
  'file.read',
  'file.release',
  'file.export',
  'share.system',
];

/** @typedef {'available'|'unsupported'|'denied'} CapStatus */

export function platformError(code, message, extra) {
  const err = new Error(message || code);
  err.code = code;
  if (extra) Object.assign(err, extra);
  return err;
}

export function validateFileName(name) {
  if (typeof name !== 'string' || !name || name.length > FILE_MAX_NAME) {
    return platformError('BAD_PARAMS', 'file name must be 1..' + FILE_MAX_NAME + ' chars');
  }
  if (/[\u0000-\u001f]/.test(name)) {
    return platformError('BAD_PARAMS', 'file name contains control characters');
  }
  return null;
}
