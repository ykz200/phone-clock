/* 活 · Clock — Service Worker v9.1 */
const CACHE_NAME = 'clock-v9-1';
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

/* 天气 / 空气 / 资讯 RSS 都是跨域数据请求，必须网络优先：
   缓存优先会让摆件一直显示首次抓到的旧读数 */
self.addEventListener('fetch', e => {
  if (e.request.method !== 'GET') return;

  let crossOrigin = false;
  try { crossOrigin = new URL(e.request.url).origin !== self.location.origin; } catch (err) {}

  if (crossOrigin) {
    e.respondWith(
      fetch(e.request).then(res => {
        if (res && res.status === 200 && res.type === 'basic') {
          const clone = res.clone();
          caches.open(CACHE_NAME).then(c => c.put(e.request, clone)).catch(() => {});
        }
        return res;
      }).catch(() => caches.match(e.request).then(hit => hit || new Response('Offline', { status: 503 })))
    );
    return;
  }

  e.respondWith(
    caches.match(e.request).then(cached => {
      const fetched = fetch(e.request).then(res => {
        if (res && res.status === 200) {
          const clone = res.clone();
          caches.open(CACHE_NAME).then(cache => cache.put(e.request, clone)).catch(() => {});
        }
        return res;
      }).catch(() => cached || new Response('Offline', { status: 503 }));
      return cached || fetched;
    })
  );
});

/* 消息中心走 SW 发通知（Android Chrome 无 new Notification 构造器），
   点击后回到页面即可，不重复入箱 */
self.addEventListener('notificationclick', e => {
  e.notification.close();
  e.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then(list => {
      for (const client of list) {
        if ('focus' in client) return client.focus();
      }
      return self.clients.openWindow ? self.clients.openWindow('./index.html') : undefined;
    })
  );
});
