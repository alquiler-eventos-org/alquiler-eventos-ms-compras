package com.alquilereventos.compras.config.cache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** Mantiene la API operativa sin Redis cuando CACHE_ENABLED=false. */
@Configuration(proxyBeanMethods = false)
@EnableCaching
@ConditionalOnProperty(prefix = "app.cache", name = "enabled", havingValue = "false")
public class DisabledCacheConfig {

    @Bean
    @Primary
    CacheManager cacheManager() {
        return new NoOpCacheManager();
    }
}
