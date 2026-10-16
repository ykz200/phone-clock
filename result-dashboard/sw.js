/* SmileTracker v10.0 — Service Worker · Nebula Pulse */
const CACHE_NAME = 'st-v10-nebula';
const ASSETS = ['./', './index.html', './icon.svg', './manifest.webmanifest'];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE_NAME).then(c => c.addAll(ASSETS)).catch(() => {}));
  self.skipWaiting();
});

self.addEventListener('activate', e => {
  e.waitUntil(
    caches.keys().then(keys =>
      Promise.all(keys.filter(k => k !== CACHE_NAME).map(k => caches.delete(k)))
    )
  );
  self.clients.claim();
});

/* 网络优先 + 缓存降级（保证联网时获取最新内容） */
self.addEventListener('fetch', e => {
  if (e.request.method !== 'GET') return;
  e.respondWith(
    fetch(e.request).then(res => {
      if (res && res.status === 200) {
        const clone = res.clone();
        caches.open(CACHE_NAME).then(cache => cache.put(e.request, clone)).catch(() => {});
      }
      return res;
    }).catch(() => {
      return caches.match(e.request).then(cached => cached || new Response('Offline', { status: 503 }));
    })
  );
});
