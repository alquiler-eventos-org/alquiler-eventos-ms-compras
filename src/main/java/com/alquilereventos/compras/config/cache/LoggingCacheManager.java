package com.alquilereventos.compras.config.cache;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** CacheManager que envuelve cada seccion para registrar GET/SET/DELETE. */
final class LoggingCacheManager implements CacheManager {

    private final CacheManager delegate;
    private final Map<String, Cache> caches = new ConcurrentHashMap<>();

    LoggingCacheManager(CacheManager delegate) {
        this.delegate = delegate;
    }

    @Override
    public Cache getCache(String name) {
        Cache cache = delegate.getCache(name);
        if (cache == null) {
            return null;
        }
        return caches.computeIfAbsent(name, ignored -> new LoggingCache(cache));
    }

    @Override
    public Collection<String> getCacheNames() {
        return delegate.getCacheNames();
    }
}
