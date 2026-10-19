package com.alquilereventos.compras.config.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** La cache nunca debe tumbar la API: si Redis falla se degrada a la BD. */
class LoggingCacheTest {

    @Test
    @DisplayName("GET devuelve null si Redis falla (fail-open)")
    void getToleraFallo() {
        Cache delegate = mock(Cache.class);
        when(delegate.getName()).thenReturn("compras:proveedores");
        when(delegate.get("k")).thenThrow(new RuntimeException("connection refused"));

        assertThat(new LoggingCache(delegate).get("k")).isNull();
    }

    @Test
    @DisplayName("PUT no propaga excepciones de Redis")
    void putToleraFallo() {
        Cache delegate = mock(Cache.class);
        when(delegate.getName()).thenReturn("compras:proveedores");
        doThrow(new RuntimeException("boom")).when(delegate).put("k", "v");

        assertThatCode(() -> new LoggingCache(delegate).put("k", "v")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("EVICT no propaga excepciones de Redis")
    void evictToleraFallo() {
        Cache delegate = mock(Cache.class);
        when(delegate.getName()).thenReturn("compras:proveedores");
        doThrow(new RuntimeException("boom")).when(delegate).evict("k");

        assertThatCode(() -> new LoggingCache(delegate).evict("k")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("El manager envuelve cada seccion con LoggingCache")
    void managerEnvuelve() {
        CacheManager delegate = mock(CacheManager.class);
        Cache cache = mock(Cache.class);
        when(cache.getName()).thenReturn("compras:proveedores");
        when(delegate.getCache("compras:proveedores")).thenReturn(cache);

        LoggingCacheManager manager = new LoggingCacheManager(delegate);
        assertThat(manager.getCache("compras:proveedores")).isInstanceOf(LoggingCache.class);
        assertThat(manager.getCache("otra")).isNull();
    }
}
