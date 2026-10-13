#!/usr/bin/env node
// Author: 张晓达
/** Node verification for backup format, limits, and import planning (no browser). */
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');

// Load backup.js via dynamic import (ESM)
const backup = await import(join(root, 'web/lib/backup.js'));
const limits = await import(join(root, 'web/lib/platformLimits.js'));

const tasks = [
  { id: 'a1', title: '中文 任务', notes: 'hello', status: 0, dueYmd: 20260101, deleted: false },
  { id: 'a2', title: 'space name', notes: '', status: 2, dueYmd: 0, deleted: false },
  { id: 'a1', title: 'dup', notes: '', status: 0, dueYmd: 0, deleted: false },
];

const { text, fileName, bytes } = backup.buildBackup(tasks);
assert.ok(fileName.includes('任务') || fileName.endsWith('.jnbackup'));
assert.ok(bytes < limits.FILE_MAX_BYTES);
assert.ok(text.includes('jnative.guide-tasks.backup'));
assert.ok(!text.includes('syncToken'));
assert.ok(!text.includes('opId'));

const ok = backup.previewBackup(text);
assert.equal(ok.ok, true);
assert.equal(ok.preview.validCount, 2); // dup id skipped in-file
assert.equal(ok.preview.duplicateInFile.length, 1);

const bad = backup.previewBackup('{ not json');
assert.equal(bad.ok, false);
assert.equal(bad.code, 'CORRUPT');

const wrong = backup.previewBackup(JSON.stringify({ format: 'other', version: 1, tasks: [] }));
assert.equal(wrong.ok, false);

const huge = 'x'.repeat(limits.FILE_MAX_BYTES + 10);
assert.equal(backup.previewBackup(huge).code, 'SIZE_LIMIT');

const planAppend = backup.planImport(ok.preview, ['a1'], 'append');
assert.equal(planAppend.toAdd.length, 1);
assert.equal(planAppend.skippedExisting[0], 'a1');

const planReplace = backup.planImport(ok.preview, ['a1'], 'replace');
assert.equal(planReplace.willClearAll, true);
assert.equal(planReplace.toAdd.length, 2);

// Chinese + spaces file name validation
assert.equal(limits.validateFileName('任务 备份.jnbackup'), null);
assert.ok(limits.validateFileName(''));
assert.ok(limits.validateFileName('a'.repeat(limits.FILE_MAX_NAME + 1)));

console.log('verify_platform_backup PASS', { fileName, bytes, valid: ok.preview.validCount });
