// LockGuard Service Worker v3
const CACHE = 'lockguard-v3';

self.addEventListener('install', e => {
  self.skipWaiting();
});

self.addEventListener('activate', e => {
  e.waitUntil(
    caches.keys().then(keys =>
      Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k)))
    )
  );
});

self.addEventListener('fetch', e => {
  // Let CDN requests go through network (face-api.js models)
  if (e.request.url.includes('cdn.jsdelivr.net')) {
    e.respondWith(
      caches.open(CACHE).then(cache =>
        cache.match(e.request).then(cached =>
          cached || fetch(e.request).then(resp => {
            cache.put(e.request, resp.clone());
            return resp;
          })
        )
      )
    );
    return;
  }
  // Default: network first
  e.respondWith(fetch(e.request).catch(() => caches.match(e.request)));
});
