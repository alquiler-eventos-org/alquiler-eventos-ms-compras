package com.alquilereventos.compras.config.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;

import java.util.concurrent.Callable;

/** Decorador que deja trazabilidad de todas las operaciones de cache. */
final class LoggingCache implements Cache {

    private static final Logger log = LoggerFactory.getLogger(LoggingCache.class);
    private final Cache delegate;

    LoggingCache(Cache delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getName() { return delegate.getName(); }

    @Override
    public Object getNativeCache() { return delegate.getNativeCache(); }

    @Override
    public ValueWrapper get(Object key) {
        try {
            ValueWrapper result = delegate.get(key);
            log.info("CACHE GET cache={} key={} result={}", getName(), key, result == null ? "MISS" : "HIT");
            return result;
        } catch (RuntimeException ex) {
            log.warn("CACHE ERROR operation=GET cache={} key={}; se continua sin cache", getName(), key, ex);
            return null;
        }
    }

    @Override
    public <T> T get(Object key, Class<T> type) {
        try {
            T result = delegate.get(key, type);
            log.info("CACHE GET cache={} key={} result={}", getName(), key, result == null ? "MISS" : "HIT");
            return result;
        } catch (RuntimeException ex) {
            log.warn("CACHE ERROR operation=GET cache={} key={}; se continua sin cache", getName(), key, ex);
            return null;
        }
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        T result = delegate.get(key, valueLoader);
        log.info("CACHE GET_OR_LOAD cache={} key={}", getName(), key);
        return result;
    }

    @Override
    public void put(Object key, Object value) {
        try {
            delegate.put(key, value);
            log.info("CACHE SET cache={} key={}", getName(), key);
        } catch (RuntimeException ex) {
            log.warn("CACHE ERROR operation=SET cache={} key={}; se continua sin cache", getName(), key, ex);
        }
    }

    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        ValueWrapper result = delegate.putIfAbsent(key, value);
        log.info("CACHE SET_IF_ABSENT cache={} key={} result={}", getName(), key,
                result == null ? "STORED" : "EXISTS");
        return result;
    }

    @Override
    public void evict(Object key) {
        try {
            delegate.evict(key);
            log.info("CACHE DELETE cache={} key={}", getName(), key);
        } catch (RuntimeException ex) {
            log.warn("CACHE ERROR operation=DELETE cache={} key={}; se continua sin cache", getName(), key, ex);
        }
    }

    @Override
    public boolean evictIfPresent(Object key) {
        boolean removed = delegate.evictIfPresent(key);
        log.info("CACHE DELETE_IF_PRESENT cache={} key={} result={}", getName(), key, removed);
        return removed;
    }

    @Override
    public void clear() {
        delegate.clear();
        log.info("CACHE CLEAR cache={}", getName());
    }

    @Override
    public boolean invalidate() {
        boolean invalidated = delegate.invalidate();
        log.info("CACHE INVALIDATE cache={} result={}", getName(), invalidated);
        return invalidated;
    }
}
