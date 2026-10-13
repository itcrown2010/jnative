// Author: 张晓达
/**
 * Sync engine: IDB transactions + SyncCore (WASM) rules + real HTTP API.
 * Single-flight via Web Locks when available; BroadcastChannel for multi-tab.
 */
import {
  idbMutate, idbListTasks, idbListOps, idbGetTask, idbGetMeta, idbSetMeta, newId,
} from './idbStore.js';
import { httpRequest, httpToSyncKind } from './httpClient.js';

const DEFAULT_BASE = 'http://127.0.0.1:8780';
const CHANNEL = 'jnative-guide-tasks-sync';

export function bindSyncApi(findExport) {
  const need = (sub) => {
    const fn = findExport(sub);
    if (typeof fn !== 'function') throw new Error('missing ' + sub);
    return fn;
  };
  return {
    mergeDecision: need('SyncCore_mergeDecision_i32_i32_i32'),
    stateAfterLocalMutation: need('SyncCore_stateAfterLocalMutation'),
    stateBeginSync: need('SyncCore_stateBeginSync_i32'),
    stateAfterAck: need('SyncCore_stateAfterAck'),
    stateAfterConflict: need('SyncCore_stateAfterConflict'),
    stateAfterTempFail: need('SyncCore_stateAfterTempFail'),
    classifyHttpError: need('SyncCore_classifyHttpError_i32_i32'),
    backoffMs: need('SyncCore_backoffMs_i32'),
    maxAttempts: need('SyncCore_maxAttempts'),
    isDeleteEditConflict: need('SyncCore_isDeleteEditConflict_i32_i32_i32_i32'),
    OP_CREATE: 1,
    OP_UPDATE: 2,
    OP_DELETE: 3,
    ST_LOCAL: 1,
    ST_PENDING: 2,
    ST_SYNCING: 3,
    ST_SYNCED: 4,
    ST_CONFLICT: 5,
    ST_FAILED: 6,
    MERGE_APPEND: 1,
    MERGE_REPLACE_LAST: 2,
    MERGE_DROP_BOTH: 3,
    MERGE_DROP_NEW: 4,
    MERGE_REJECT: 5,
    ERR_TEMP: 1,
    ERR_AUTH: 2,
    ERR_VALIDATION: 3,
    ERR_CONFLICT: 4,
    ERR_PERM: 5,
  };
}

function opTypeName(n) {
  if (n === 1) return 'create';
  if (n === 2) return 'update';
  if (n === 3) return 'delete';
  return 'unknown';
}

function opTypeNum(s) {
  if (s === 'create') return 1;
  if (s === 'update') return 2;
  if (s === 'delete') return 3;
  return 0;
}

export function createSyncEngine({ syncApi, getBaseUrl, getToken, onChange, onAuthExpired }) {
  let online = typeof navigator !== 'undefined' ? navigator.onLine : true;
  let syncing = false;
  let disposed = false;
  let attempts = 0;
  let pullGen = 0;
  const bc = typeof BroadcastChannel !== 'undefined' ? new BroadcastChannel(CHANNEL) : null;

  function emit() {
    if (onChange) onChange(getStatusSnapshot());
    if (bc) {
      try { bc.postMessage({ type: 'state' }); } catch (_) {}
    }
  }

  function getStatusSnapshot() {
    return { online, syncing, attempts };
  }

  async function pendingCount() {
    const ops = await idbListOps();
    return ops.length;
  }

  /**
   * Local mutation + queue in ONE IDB transaction. Only then report saved.
   */
  async function applyLocalChange({ type, taskId, payload, expectedVersion }) {
    const opNum = opTypeNum(type);
    if (!opNum) throw new Error('bad type');

    const result = await idbMutate(async (api) => {
      const existingOps = await api.getOps(taskId);
      const last = existingOps.length ? existingOps[existingOps.length - 1] : null;
      const lastType = last ? opTypeNum(last.type) : 0;
      let task = await api.getTask(taskId);
      const createStillLocal = task && task.serverVersion === 0 && !task.everSynced ? 1 : 0;
      const decision = syncApi.mergeDecision(lastType, opNum, createStillLocal) | 0;

      if (decision === syncApi.MERGE_REJECT) {
        return { ok: false, error: '队列合并拒绝' };
      }
      if (decision === syncApi.MERGE_DROP_NEW) {
        return { ok: true, dropped: true, task };
      }
      if (decision === syncApi.MERGE_DROP_BOTH) {
        if (last) await api.deleteOp(last.opId);
        if (task) {
          task.deleted = true;
          task.syncState = syncApi.stateAfterAck();
          await api.putTask(task);
        }
        return { ok: true, droppedBoth: true, task };
      }

      const syncState = syncApi.stateAfterLocalMutation() | 0;
      const now = Date.now();
      if (type === 'create') {
        task = {
          id: taskId,
          title: payload.title || '',
          notes: payload.notes || '',
          status: payload.status | 0,
          dueYmd: payload.dueYmd | 0,
          serverVersion: 0,
          syncState,
          deleted: false,
          everSynced: false,
          conflictServer: null,
          updatedAt: now,
        };
      } else if (type === 'update') {
        if (!task || task.deleted) return { ok: false, error: '任务不存在' };
        task = {
          ...task,
          title: payload.title || '',
          notes: payload.notes || '',
          status: payload.status | 0,
          dueYmd: payload.dueYmd | 0,
          syncState,
          conflictServer: null,
          updatedAt: now,
        };
      } else {
        if (!task) {
          task = {
            id: taskId,
            title: '',
            notes: '',
            status: 0,
            dueYmd: 0,
            serverVersion: expectedVersion | 0,
            syncState,
            deleted: true,
            everSynced: false,
            conflictServer: null,
            updatedAt: now,
          };
        } else {
          task = { ...task, deleted: true, syncState, conflictServer: null, updatedAt: now };
        }
      }
      await api.putTask(task);

      const op = {
        opId: newId('op-'),
        taskId,
        type,
        expectedVersion: expectedVersion | 0,
        payload: payload || {},
        createdAt: now,
      };
      if (decision === syncApi.MERGE_REPLACE_LAST && last) {
        await api.deleteOp(last.opId);
        if (last.type === 'create' && type === 'update') {
          op.type = 'create';
          op.expectedVersion = 0;
        }
      }
      await api.putOp(op);
      return { ok: true, task, op };
    });

    emit();
    return result;
  }

  function authHeaders() {
    const token = getToken && getToken();
    return token ? { Authorization: 'Bearer ' + token } : {};
  }

  async function pullChanges() {
    const gen = ++pullGen;
    const base = getBaseUrl() || DEFAULT_BASE;
    const since = Number((await idbGetMeta('changeCursor')) || 0);
    const result = await httpRequest(
      base.replace(/\/$/, '') + '/v1/changes?since=' + since + '&limit=50',
      { method: 'GET', headers: authHeaders(), timeoutMs: 20_000 },
    );
    if (disposed || gen !== pullGen) return; // late response after nav/logout
    if (result.kind === 'http_error' && (result.status === 401 || result.code === 'auth_expired')) {
      if (onAuthExpired) onAuthExpired(result);
      throw Object.assign(new Error('auth'), { httpStatus: 401 });
    }
    if (result.kind !== 'ok') {
      throw Object.assign(new Error(result.message || result.kind), { httpStatus: result.status || 0 });
    }
    const data = result.json || {};
    for (const ch of data.changes || []) {
      if (disposed || gen !== pullGen) return;
      await idbMutate(async (api) => {
        const local = await api.getTask(ch.id);
        const pending = await api.getOps(ch.id);
        if (pending.length) {
          // Do not overwrite local pending with server silently
          return;
        }
        if (local && local.syncState === syncApi.ST_CONFLICT) return;
        await api.putTask({
          id: ch.id,
          title: ch.title || '',
          notes: ch.notes || '',
          status: ch.status | 0,
          dueYmd: ch.dueYmd | 0,
          serverVersion: ch.version | 0,
          syncState: syncApi.ST_SYNCED,
          deleted: !!ch.deleted,
          everSynced: true,
          conflictServer: null,
          updatedAt: Date.now(),
        });
      });
    }
    if (data.next != null && !disposed && gen === pullGen) {
      await idbSetMeta('changeCursor', data.next);
    }
  }

  async function pushOne(op) {
    const base = getBaseUrl() || DEFAULT_BASE;
    const result = await httpRequest(base.replace(/\/$/, '') + '/v1/ops', {
      method: 'POST',
      headers: authHeaders(),
      body: {
        opId: op.opId,
        type: op.type,
        taskId: op.taskId,
        expectedVersion: op.expectedVersion,
        payload: op.payload,
      },
      timeoutMs: 20_000,
    });
    if (disposed) return { kind: 'cancelled' };
    const mapped = httpToSyncKind(result, syncApi);
    if (mapped.kind === 'auth' && onAuthExpired) onAuthExpired(result);
    // Non-idempotent write: only retry on temp with same opId (server idempotency)
    return mapped;
  }

  async function runSyncLoop() {
    if (disposed || syncing || !online) return;
    const run = async () => {
      syncing = true;
      emit();
      try {
        try { await pullChanges(); } catch (_) { /* pull best-effort */ }
        const max = syncApi.maxAttempts() | 0;
        while (!disposed && online) {
          const ops = await idbListOps();
          if (!ops.length) {
            attempts = 0;
            break;
          }
          const op = ops[0];
          await idbMutate(async (api) => {
            const t = await api.getTask(op.taskId);
            if (t) {
              t.syncState = syncApi.stateBeginSync(t.syncState | 0) | 0;
              await api.putTask(t);
            }
          });
          const res = await pushOne(op);
          if (res.kind === 'ok') {
            attempts = 0;
            await idbMutate(async (api) => {
              await api.deleteOp(op.opId);
              const t = await api.getTask(op.taskId);
              if (t && res.task) {
                t.title = res.task.title || t.title;
                t.notes = res.task.notes || '';
                t.status = res.task.status | 0;
                t.dueYmd = res.task.dueYmd | 0;
                t.serverVersion = res.task.version | 0;
                t.deleted = !!res.task.deleted;
                t.everSynced = true;
                t.syncState = syncApi.stateAfterAck() | 0;
                t.conflictServer = null;
                await api.putTask(t);
              }
            });
            emit();
            continue;
          }
          if (res.kind === 'conflict') {
            await idbMutate(async (api) => {
              const t = await api.getTask(op.taskId);
              if (t) {
                t.syncState = syncApi.stateAfterConflict() | 0;
                t.conflictServer = res.server || null;
                await api.putTask(t);
              }
              // Keep op for user resolution (re-submit or take server)
            });
            emit();
            break;
          }
          if (res.kind === 'cancelled') {
            emit();
            break;
          }
          if (res.kind === 'temp') {
            // Idempotent retry only (same opId); never invent a new write
            attempts += 1;
            await idbMutate(async (api) => {
              const t = await api.getTask(op.taskId);
              if (t) {
                t.syncState = syncApi.stateAfterTempFail() | 0;
                await api.putTask(t);
              }
            });
            emit();
            if (attempts >= max) break;
            const wait = syncApi.backoffMs(attempts) | 0;
            await new Promise((r) => setTimeout(r, wait));
            continue;
          }
          // auth / validation / perm — stop for user (no auto-retry of non-idempotent invent)
          await idbMutate(async (api) => {
            const t = await api.getTask(op.taskId);
            if (t) {
              t.syncState = syncApi.stateAfterTempFail() | 0;
              if (res.kind === 'validation' && res.fields) {
                t.lastValidation = res.fields;
              }
              await api.putTask(t);
            }
          });
          emit();
          break;
        }
      } finally {
        syncing = false;
        emit();
      }
    };

    if (typeof navigator !== 'undefined' && navigator.locks && navigator.locks.request) {
      try {
        await navigator.locks.request('jnative-guide-tasks-sync', run);
        return;
      } catch (_) { /* fall through */ }
    }
    await run();
  }

  async function resolveConflictTakeServer(taskId) {
    await idbMutate(async (api) => {
      const t = await api.getTask(taskId);
      if (!t || !t.conflictServer) return;
      const s = t.conflictServer;
      const ops = await api.getOps(taskId);
      for (const o of ops) await api.deleteOp(o.opId);
      await api.putTask({
        id: taskId,
        title: s.title || '',
        notes: s.notes || '',
        status: s.status | 0,
        dueYmd: s.dueYmd | 0,
        serverVersion: s.version | 0,
        syncState: syncApi.ST_SYNCED,
        deleted: !!s.deleted,
        everSynced: true,
        conflictServer: null,
        updatedAt: Date.now(),
      });
    });
    emit();
  }

  async function resolveConflictResubmitLocal(taskId) {
    await idbMutate(async (api) => {
      const t = await api.getTask(taskId);
      if (!t || !t.conflictServer) return;
      const serverVer = t.conflictServer.version | 0;
      const ops = await api.getOps(taskId);
      for (const o of ops) await api.deleteOp(o.opId);
      if (t.deleted) {
        await api.putOp({
          opId: newId('op-'),
          taskId,
          type: 'delete',
          expectedVersion: serverVer,
          payload: {},
          createdAt: Date.now(),
        });
      } else if (!t.everSynced && t.serverVersion === 0) {
        await api.putOp({
          opId: newId('op-'),
          taskId,
          type: 'create',
          expectedVersion: 0,
          payload: { title: t.title, notes: t.notes, status: t.status, dueYmd: t.dueYmd },
          createdAt: Date.now(),
        });
      } else {
        await api.putOp({
          opId: newId('op-'),
          taskId,
          type: 'update',
          expectedVersion: serverVer,
          payload: { title: t.title, notes: t.notes, status: t.status, dueYmd: t.dueYmd },
          createdAt: Date.now(),
        });
      }
      t.syncState = syncApi.ST_PENDING;
      t.conflictServer = null;
      await api.putTask(t);
    });
    emit();
    scheduleSync();
  }

  let syncTimer = null;
  function scheduleSync() {
    if (disposed) return;
    clearTimeout(syncTimer);
    syncTimer = setTimeout(() => { runSyncLoop().catch(() => {}); }, 50);
  }

  function setOnline(v) {
    online = !!v;
    emit();
    if (online) scheduleSync();
  }

  if (typeof window !== 'undefined') {
    window.addEventListener('online', () => setOnline(true));
    window.addEventListener('offline', () => setOnline(false));
    document.addEventListener('visibilitychange', () => {
      if (!document.hidden) scheduleSync();
    });
  }
  if (bc) {
    bc.onmessage = (ev) => {
      if (ev.data && ev.data.type === 'sync-please') scheduleSync();
    };
  }

  return {
    applyLocalChange,
    scheduleSync,
    runSyncLoop,
    resolveConflictTakeServer,
    resolveConflictResubmitLocal,
    pendingCount,
    listTasks: idbListTasks,
    listOps: idbListOps,
    getTask: idbGetTask,
    getStatusSnapshot,
    setOnline,
    dispose() {
      disposed = true;
      pullGen += 1;
      clearTimeout(syncTimer);
      if (bc) bc.close();
    },
    /** Call on logout so in-flight pulls cannot mutate the next user's store. */
    invalidateInFlight() {
      pullGen += 1;
    },
    newTaskId: () => newId('task-'),
  };
}

export function syncStateLabel(st) {
  if (st === 1) return '本地已保存';
  if (st === 2) return '待同步';
  if (st === 3) return '同步中';
  if (st === 4) return '已同步';
  if (st === 5) return '冲突';
  if (st === 6) return '失败';
  return '未知';
}
