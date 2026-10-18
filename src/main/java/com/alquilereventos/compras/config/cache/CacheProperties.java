package com.alquilereventos.compras.config.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/** Valores de Redis y de la politica de cache, todos externos al codigo. */
@ConfigurationProperties(prefix = "app.cache")
public class CacheProperties {

    private boolean enabled = true;
    private String keyPrefix;
    private String environment;
    private String keyVersion;
    private Map<String, Domain> domains = new LinkedHashMap<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getKeyPrefix() { return keyPrefix; }
    public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public String getKeyVersion() { return keyVersion; }
    public void setKeyVersion(String keyVersion) { this.keyVersion = keyVersion; }
    public Map<String, Domain> getDomains() { return domains; }
    public void setDomains(Map<String, Domain> domains) { this.domains = domains; }

    public static class Domain {
        private String name;
        private String keyType;
        private long ttlSeconds;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getKeyType() { return keyType; }
        public void setKeyType(String keyType) { this.keyType = keyType; }
        public long getTtlSeconds() { return ttlSeconds; }
        public void setTtlSeconds(long ttlSeconds) { this.ttlSeconds = ttlSeconds; }
    }
}
