// Author: 张晓达
/**
 * Offline shell cache — one versioned bundle (html/js/css/wasm).
 * Never caches /v1/* API. On update failure keep previous complete set.
 * Does not delete IndexedDB business data.
 */
const VERSION = 'guide-tasks-sw-v3';
const ASSETS = [
  './',
  './index.html',
  './app-shell.js',
  './app.js',
  './app.wasm',
  './tasks.css',
  './api/hint.json',
  './lib/router.js',
  './lib/lifecycle.js',
  './lib/taskApi.js',
  './lib/formController.js',
  './lib/storage.js',
  './lib/idbStore.js',
  './lib/syncEngine.js',
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(VERSION).then(async (cache) => {
      // Atomic-ish: if any asset fails, abort install (keep old SW)
      await cache.addAll(ASSETS);
    }).then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== VERSION).map((k) => caches.delete(k)))
    ).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);
  if (url.pathname.includes('/v1/') || url.hostname === '127.0.0.1' && url.port === '8780') {
    return; // network only for sync API
  }
  if (event.request.method !== 'GET') return;
  event.respondWith(
    caches.open(VERSION).then(async (cache) => {
      const cached = await cache.match(event.request);
      if (cached) return cached;
      try {
        const resp = await fetch(event.request);
        if (resp.ok && url.origin === self.location.origin) {
          cache.put(event.request, resp.clone());
        }
        return resp;
      } catch (_) {
        const fallback = await cache.match('./index.html');
        return fallback || Response.error();
      }
    })
  );
});
