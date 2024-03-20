package com.raitonbl.hermes.smsc.camel.system.datasource;

import com.raitonbl.hermes.smsc.camel.model.Entity;
import com.raitonbl.hermes.smsc.camel.model.PolicyDefinition;
import com.raitonbl.hermes.smsc.camel.model.SmppConnectionDefinition;
import lombok.Getter;

@Getter
public enum DatasourceType {
    POLICY(PolicyDefinition.class, "policies"),
    SMPP_CONNECTION(SmppConnectionDefinition.class, "smpp-connections");
    public final String prefix;
    public final Class<? extends Entity> javaType;

    DatasourceType(Class<? extends Entity> javaType, String prefix) {
        this.prefix = prefix;
        this.javaType = javaType;
    }
}
