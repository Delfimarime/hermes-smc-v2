package com.raitonbl.hermes.smsc.camel.model;

import com.raitonbl.hermes.smsc.config.smpp.SmppConfiguration;
import lombok.Data;
import lombok.ToString;

import java.util.Map;

@Data
@ToString
public class SmppConnectionDefinition {
    private String id;
    private String name;
    private String alias;
    private String description;
    private Map<String, String> tags;
    private SmppConfiguration configuration;
}
