// Author: 张晓达
/**
 * Task backup format v1 — business data only.
 * Never includes sync tokens, auth, or pending op queues.
 */
import { FILE_MAX_BYTES, platformError } from './platformLimits.js';

export const BACKUP_FORMAT = 'jnative.guide-tasks.backup';
export const BACKUP_VERSION = 1;
export const BACKUP_MIME = 'application/json';
export const BACKUP_EXT = '.jnbackup';

/** Strip sync / credential fields from a stored task. */
export function taskToBackupRecord(t) {
  if (!t || typeof t !== 'object') return null;
  const id = typeof t.id === 'string' ? t.id.trim() : '';
  if (!id || id.length > 64) return null;
  const title = typeof t.title === 'string' ? t.title : '';
  if (title.length > 200) return null;
  const notes = typeof t.notes === 'string' ? t.notes : '';
  if (notes.length > 4000) return null;
  const status = t.status | 0;
  if (status < 0 || status > 3) return null;
  const dueYmd = t.dueYmd | 0;
  return {
    id,
    title,
    notes,
    status,
    dueYmd,
    deleted: !!t.deleted,
  };
}

export function buildBackup(tasks, { appVersion = '0.1.0' } = {}) {
  const list = [];
  for (const t of tasks || []) {
    if (t && t.deleted) continue;
    const rec = taskToBackupRecord(t);
    if (rec) list.push(rec);
  }
  const doc = {
    format: BACKUP_FORMAT,
    version: BACKUP_VERSION,
    exportedAt: new Date().toISOString(),
    appVersion: String(appVersion),
    tasks: list,
  };
  const text = JSON.stringify(doc, null, 2);
  const bytes = new TextEncoder().encode(text).length;
  if (bytes > FILE_MAX_BYTES) {
    throw platformError('SIZE_LIMIT', 'backup exceeds ' + FILE_MAX_BYTES + ' bytes', { bytes });
  }
  return { doc, text, bytes, fileName: '任务备份' + BACKUP_EXT };
}

/**
 * Parse and validate backup text. Does not mutate storage.
 * @returns {{ ok:true, preview } | { ok:false, error, code }}
 */
export function previewBackup(text) {
  if (typeof text !== 'string') {
    return { ok: false, code: 'BAD_FORMAT', error: '备份必须是 UTF-8 文本' };
  }
  if (new TextEncoder().encode(text).length > FILE_MAX_BYTES) {
    return { ok: false, code: 'SIZE_LIMIT', error: '文件超过大小限制' };
  }
  let doc;
  try {
    doc = JSON.parse(text);
  } catch {
    return { ok: false, code: 'CORRUPT', error: 'JSON 损坏或不是有效备份' };
  }
  if (!doc || doc.format !== BACKUP_FORMAT) {
    return { ok: false, code: 'BAD_FORMAT', error: '不是任务备份格式' };
  }
  if ((doc.version | 0) !== BACKUP_VERSION) {
    return { ok: false, code: 'BAD_VERSION', error: '不支持的备份版本: ' + doc.version };
  }
  if (!Array.isArray(doc.tasks)) {
    return { ok: false, code: 'BAD_FORMAT', error: 'tasks 必须是数组' };
  }
  const valid = [];
  const invalid = [];
  const seen = new Set();
  const dupInFile = [];
  for (let i = 0; i < doc.tasks.length; i++) {
    const rec = taskToBackupRecord(doc.tasks[i]);
    if (!rec) {
      invalid.push(i);
      continue;
    }
    if (seen.has(rec.id)) {
      dupInFile.push(rec.id);
      continue;
    }
    seen.add(rec.id);
    valid.push(rec);
  }
  return {
    ok: true,
    preview: {
      format: doc.format,
      version: doc.version | 0,
      exportedAt: doc.exportedAt || '',
      appVersion: doc.appVersion || '',
      total: doc.tasks.length,
      validCount: valid.length,
      invalidCount: invalid.length,
      duplicateInFile: dupInFile,
      sampleTitles: valid.slice(0, 5).map((t) => t.title),
      records: valid,
    },
  };
}

/**
 * Plan import against existing task ids.
 * strategy: 'append' (skip existing ids) | 'replace' (clear business tasks then import)
 */
export function planImport(preview, existingIds, strategy) {
  const existing = new Set(existingIds || []);
  const toAdd = [];
  const skippedExisting = [];
  for (const rec of preview.records) {
    if (strategy === 'append' && existing.has(rec.id)) {
      skippedExisting.push(rec.id);
      continue;
    }
    toAdd.push(rec);
  }
  return {
    strategy,
    toAdd,
    skippedExisting,
    willClearAll: strategy === 'replace',
    note:
      strategy === 'replace'
        ? '替换将清除本地任务业务数据；不同步令牌；导入项视为未同步本地变更'
        : '追加跳过已有 ID；导入项视为未同步，不会携带旧 serverVersion',
  };
}

