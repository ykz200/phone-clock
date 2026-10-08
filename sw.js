const CACHE_NAME = 'v2';

self.addEventListener('install', function(e) {
  self.skipWaiting();
});

self.addEventListener('activate', function(e) {
  self.clients.claim();
  // 清理旧版本缓存
  e.waitUntil(
    caches.keys().then(function(keys) {
      return Promise.all(
        keys.filter(function(key) { return key !== CACHE_NAME; })
            .map(function(key) { return caches.delete(key); })
      );
    })
  );
});

self.addEventListener('fetch', function(e) {
  // HTML 请求始终走网络优先，确保更新及时生效
  if (e.request.destination === 'document' || e.request.url.endsWith('.html')) {
    e.respondWith(
      fetch(e.request).then(function(resp) {
        var clone = resp.clone();
        caches.open(CACHE_NAME).then(function(cache) { cache.put(e.request, clone); });
        return resp;
      }).catch(function() { return caches.match(e.request); })
    );
    return;
  }
  // 其他资源：缓存优先
  e.respondWith(
    caches.match(e.request).then(function(r) { return r || fetch(e.request).then(function(resp) {
      var clone = resp.clone();
      caches.open(CACHE_NAME).then(function(cache) { cache.put(e.request, clone); });
      return resp;
    }).catch(function() { return caches.match('./index.html'); }); })
  );
});
