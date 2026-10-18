package com.alquilereventos.compras.config.cache;

import org.springframework.stereotype.Component;

/** Genera claves de negocio legibles sin IDs ambiguos entre dominios. */
@Component("cacheKeys")
public class CacheKeyFactory {

    private final CacheProperties properties;

    public CacheKeyFactory(CacheProperties properties) {
        this.properties = properties;
    }

    public String proveedor(Integer id) {
        return key("proveedores", id);
    }

    public String ordenCompra(Integer id) {
        return key("ordenes-compra", id);
    }

    private String key(String domain, Integer id) {
        CacheProperties.Domain definition = properties.getDomains().get(domain);
        if (definition == null || definition.getKeyType() == null) {
            throw new IllegalStateException("No existe configuracion de clave para el dominio " + domain);
        }
        return String.join(":", properties.getKeyPrefix(), properties.getEnvironment(),
                "compras", definition.getKeyType(), properties.getKeyVersion(), String.valueOf(id));
    }
}
