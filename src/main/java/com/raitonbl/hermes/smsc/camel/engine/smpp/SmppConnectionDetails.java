package com.raitonbl.hermes.smsc.camel.engine.smpp;

import com.raitonbl.hermes.smsc.camel.model.PolicyDefinition;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SmppConnectionDetails {
    private String id;
    private String name;
    private String alias;
    private PolicyDefinition policy;
}
