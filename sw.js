self.addEventListener('install', function(e) { self.skipWaiting(); });
self.addEventListener('activate', function(e) { self.clients.claim(); });
self.addEventListener('fetch', function(e) {
  e.respondWith(
    caches.match(e.request).then(function(r) { return r || fetch(e.request).then(function(resp) {
      var clone = resp.clone();
      caches.open('v1').then(function(cache) { cache.put(e.request, clone); });
      return resp;
    }).catch(function() { return caches.match('./index.html'); }); })
  );
});
