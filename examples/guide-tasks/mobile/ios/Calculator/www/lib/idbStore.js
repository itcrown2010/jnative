// Author: 张晓达
/**
 * IndexedDB transactional store for tasks + sync queue.
 * NOT localStorage — mutations and queue ops commit in one IDB transaction.
 * Ordinary local data store — not a secure credential store.
 */

const DB_NAME = 'jnative-guide-tasks-sync';
const DB_VERSION = 1;

function openDb() {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains('tasks')) {
        db.createObjectStore('tasks', { keyPath: 'id' });
      }
      if (!db.objectStoreNames.contains('ops')) {
        const ops = db.createObjectStore('ops', { keyPath: 'opId' });
        ops.createIndex('byTask', 'taskId', { unique: false });
        ops.createIndex('byCreated', 'createdAt', { unique: false });
      }
      if (!db.objectStoreNames.contains('meta')) {
        db.createObjectStore('meta', { keyPath: 'key' });
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

function txDone(tx) {
  return new Promise((resolve, reject) => {
    tx.oncomplete = () => resolve();
    tx.onabort = () => reject(tx.error || new Error('idb abort'));
    tx.onerror = () => reject(tx.error);
  });
}

function reqToPromise(req) {
  return new Promise((resolve, reject) => {
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

export async function idbGetMeta(key, fallback = null) {
  const db = await openDb();
  const tx = db.transaction('meta', 'readonly');
  const row = await reqToPromise(tx.objectStore('meta').get(key));
  await txDone(tx);
  return row ? row.value : fallback;
}

export async function idbSetMeta(key, value) {
  const db = await openDb();
  const tx = db.transaction('meta', 'readwrite');
  tx.objectStore('meta').put({ key, value });
  await txDone(tx);
}

export async function idbListTasks() {
  const db = await openDb();
  const tx = db.transaction('tasks', 'readonly');
  const all = await reqToPromise(tx.objectStore('tasks').getAll());
  await txDone(tx);
  return (all || []).filter((t) => !t.deleted);
}

export async function idbGetTask(id) {
  const db = await openDb();
  const tx = db.transaction('tasks', 'readonly');
  const row = await reqToPromise(tx.objectStore('tasks').get(id));
  await txDone(tx);
  return row || null;
}

export async function idbListOps() {
  const db = await openDb();
  const tx = db.transaction('ops', 'readonly');
  const all = await reqToPromise(tx.objectStore('ops').getAll());
  await txDone(tx);
  return (all || []).sort((a, b) => a.createdAt - b.createdAt);
}

export async function idbOpsForTask(taskId) {
  const db = await openDb();
  const tx = db.transaction('ops', 'readonly');
  const idx = tx.objectStore('ops').index('byTask');
  const all = await reqToPromise(idx.getAll(taskId));
  await txDone(tx);
  return (all || []).sort((a, b) => a.createdAt - b.createdAt);
}

/**
 * Atomically apply task mutation + queue merge.
 * mutator(tasksStore, opsStore) must be sync and only use store APIs that return requests we await.
 */
export async function idbMutate(fn) {
  const db = await openDb();
  const tx = db.transaction(['tasks', 'ops', 'meta'], 'readwrite');
  const tasks = tx.objectStore('tasks');
  const ops = tx.objectStore('ops');
  const meta = tx.objectStore('meta');
  const api = {
    getTask: (id) => reqToPromise(tasks.get(id)),
    putTask: (row) => reqToPromise(tasks.put(row)),
    deleteTask: (id) => reqToPromise(tasks.delete(id)),
    getOps: async (taskId) => {
      const rows = await reqToPromise(ops.index('byTask').getAll(taskId));
      return (rows || []).sort((a, b) => a.createdAt - b.createdAt);
    },
    putOp: (row) => reqToPromise(ops.put(row)),
    deleteOp: (opId) => reqToPromise(ops.delete(opId)),
    getMeta: async (key) => {
      const r = await reqToPromise(meta.get(key));
      return r ? r.value : null;
    },
    setMeta: (key, value) => reqToPromise(meta.put({ key, value })),
  };
  const result = await fn(api);
  await txDone(tx);
  return result;
}

export async function idbClearAllBusiness() {
  const db = await openDb();
  const tx = db.transaction(['tasks', 'ops', 'meta'], 'readwrite');
  await reqToPromise(tx.objectStore('tasks').clear());
  await reqToPromise(tx.objectStore('ops').clear());
  // keep schema marker
  await reqToPromise(tx.objectStore('meta').put({ key: 'clearedAt', value: Date.now() }));
  await txDone(tx);
}

export function newId(prefix) {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return (prefix || '') + crypto.randomUUID();
  }
  return (prefix || '') + 'id-' + Date.now() + '-' + Math.random().toString(16).slice(2);
}
