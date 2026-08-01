/*
 * Service Worker —— PWA 离线缓存与添加到主屏幕支持
 * 狐猴浏览器等 Chromium 内核浏览器要求注册 SW 后才允许安装 PWA
 */

/* 缓存名称，更新版本号可强制刷新缓存 */
const CACHE_NAME = 'lottery-app-v1';

/* 需要预缓存的核心文件列表 */
const PRE_CACHE_URLS = [
    '/',
    '/index.html',
    '/manifest.json',
    '/lottery.css'
];

/*
 * install 事件：预缓存核心静态资源，确保离线可访问
 */
self.addEventListener('install', event => {
    event.waitUntil(
        caches.open(CACHE_NAME).then(cache => {
            return cache.addAll(PRE_CACHE_URLS);
        })
    );
    /* 立即激活，不等待旧 SW 释放 */
    self.skipWaiting();
});

/*
 * activate 事件：清理旧版本缓存
 */
self.addEventListener('activate', event => {
    event.waitUntil(
        caches.keys().then(keys => {
            return Promise.all(
                keys.filter(key => key !== CACHE_NAME)
                    .map(key => caches.delete(key))
            );
        })
    );
    /* 立即接管所有页面 */
    self.clients.claim();
});

/*
 * fetch 事件：优先从缓存返回，缓存未命中时走网络并动态缓存
 */
self.addEventListener('fetch', event => {
    /* 跳过非 HTTP/HTTPS 请求（如 chrome-extension://） */
    if (!event.request.url.startsWith('http')) return;

    event.respondWith(
        caches.match(event.request).then(cached => {
            if (cached) return cached;
            return fetch(event.request).then(response => {
                /* 仅缓存成功的 GET 请求 */
                if (event.request.method === 'GET' && response.status === 200) {
                    const clone = response.clone();
                    caches.open(CACHE_NAME).then(cache => {
                        cache.put(event.request, clone);
                    });
                }
                return response;
            });
        })
    );
});
