package com.raitonbl.hermes.smsc.camel.engine;

import com.raitonbl.hermes.smsc.camel.model.PolicyDefinition;
import lombok.Builder;
import lombok.Getter;

import java.io.Serializable;

@Getter
@Builder
public class SmppConnectionObject implements Serializable {
    private String id;
    private String name;
    private String alias;
    private PolicyDefinition policy;
}
