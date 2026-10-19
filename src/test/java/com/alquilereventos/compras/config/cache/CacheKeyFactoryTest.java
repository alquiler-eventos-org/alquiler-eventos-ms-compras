package com.alquilereventos.compras.config.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CacheKeyFactoryTest {

    private CacheProperties properties;
    private CacheKeyFactory cacheKeys;

    @BeforeEach
    void setUp() {
        properties = new CacheProperties();
        properties.setKeyPrefix("ae");
        properties.setEnvironment("local");
        properties.setKeyVersion("v1");
        properties.setDomains(new LinkedHashMap<>());

        CacheProperties.Domain proveedores = new CacheProperties.Domain();
        proveedores.setName("compras:proveedores");
        proveedores.setKeyType("proveedor");
        proveedores.setTtlSeconds(1800);
        properties.getDomains().put("proveedores", proveedores);

        CacheProperties.Domain ordenes = new CacheProperties.Domain();
        ordenes.setName("compras:ordenes-compra");
        ordenes.setKeyType("orden-compra");
        ordenes.setTtlSeconds(120);
        properties.getDomains().put("ordenes-compra", ordenes);

        cacheKeys = new CacheKeyFactory(properties);
    }

    @Test
    @DisplayName("Formato legible ae:<ambiente>:compras:<tipo>:v1:<id>")
    void formatoDeClave() {
        assertThat(cacheKeys.proveedor(7)).isEqualTo("ae:local:compras:proveedor:v1:7");
        assertThat(cacheKeys.ordenCompra(5)).isEqualTo("ae:local:compras:orden-compra:v1:5");
    }

    @Test
    @DisplayName("Dominios distintos con el mismo id no colisionan")
    void sinColisiones() {
        assertThat(cacheKeys.proveedor(1)).isNotEqualTo(cacheKeys.ordenCompra(1));
    }

    @Test
    @DisplayName("Dominio sin configuracion falla rapido")
    void dominioDesconocido() {
        CacheProperties sinDominios = new CacheProperties();
        sinDominios.setKeyPrefix("ae");
        sinDominios.setEnvironment("local");
        sinDominios.setKeyVersion("v1");
        assertThatThrownBy(() -> new CacheKeyFactory(sinDominios).proveedor(1))
                .isInstanceOf(IllegalStateException.class);
    }
}
