package com.alquilereventos.compras.config.cache;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheOperationInvocationContext;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Spring NO resuelve placeholders "${...}" dentro de cacheNames de
 * {@code @Cacheable/@CachePut/@CacheEvict}: sin esto la seccion se llamaria
 * literalmente "${app.cache.domains.proveedores.name}" y perderia su TTL.
 * Este CacheResolver resuelve el nombre contra application.yml antes de
 * pedirle la seccion al CacheManager, manteniendo los nombres configurables.
 */
@Configuration(proxyBeanMethods = false)
public class CacheResolverConfig implements CachingConfigurer {

    private final ObjectProvider<CacheManager> cacheManager;
    private final Environment environment;

    public CacheResolverConfig(ObjectProvider<CacheManager> cacheManager, Environment environment) {
        this.cacheManager = cacheManager;
        this.environment = environment;
    }

    @Override
    public CacheResolver cacheResolver() {
        return new PlaceholderCacheResolver();
    }

    private final class PlaceholderCacheResolver implements CacheResolver {

        @Override
        public Collection<? extends Cache> resolveCaches(CacheOperationInvocationContext<?> context) {
            CacheManager manager = cacheManager.getObject();
            List<Cache> caches = new ArrayList<>();
            for (String rawName : context.getOperation().getCacheNames()) {
                String name = environment.resolveRequiredPlaceholders(rawName);
                Cache cache = manager.getCache(name);
                if (cache == null) {
                    throw new IllegalArgumentException("No existe la seccion de cache '" + name + "'");
                }
                caches.add(cache);
            }
            return caches;
        }
    }
}
