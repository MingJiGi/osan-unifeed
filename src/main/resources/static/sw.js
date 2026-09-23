// 유니피드 서비스워커 — 홈 화면 설치(PWA) 목적이 우선이라 범위를 최소로 잡는다.
// 정적 자산(css/js/이미지/폰트)만 캐시하고, 페이지·API 응답은 절대 캐시하지 않는다
// (지도/커뮤니티/신고 처리 같은 실시간 데이터를 오프라인 캐시로 보여주면 오히려 혼란만 줌).
const CACHE_NAME = 'unifeed-static-v1';

self.addEventListener('install', (event) => {
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((names) =>
      Promise.all(names.filter((n) => n !== CACHE_NAME).map((n) => caches.delete(n)))
    )
  );
  self.clients.claim();
});

function isStaticAsset(url) {
  return /\.(css|js|png|jpg|jpeg|svg|woff2?)$/.test(url.pathname);
}

self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);
  if (event.request.method !== 'GET' || url.origin !== self.location.origin || !isStaticAsset(url)) {
    return; // 정적 자산이 아니면 그냥 네트워크로 통과 — 캐시 개입 안 함
  }

  event.respondWith(
    caches.open(CACHE_NAME).then((cache) =>
      cache.match(event.request).then((cached) => {
        const fetchPromise = fetch(event.request)
          .then((res) => {
            if (res.ok) cache.put(event.request, res.clone());
            return res;
          })
          .catch(() => cached);
        return cached || fetchPromise;
      })
    )
  );
});
