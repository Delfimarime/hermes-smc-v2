package com.raitonbl.hermes.smsc.config.integration;

import org.springframework.vault.core.VaultTemplate;

public enum Type {
    HASHICORP_VAULT(VaultTemplate.class);

    public final Class<?>javaType;

    Type(Class<?> javaType) {
        this.javaType = javaType;
    }
}
