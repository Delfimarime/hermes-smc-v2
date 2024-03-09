package com.raitonbl.hermes.smsc.camel.engine.policy;

import com.raitonbl.hermes.smsc.camel.model.PolicyDefinition;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SmppTarget {
    private String id;
    private String name;
    private String alias;
    private PolicyDefinition policy;
}
