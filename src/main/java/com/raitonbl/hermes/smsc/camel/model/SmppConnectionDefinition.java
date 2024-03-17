package com.raitonbl.hermes.smsc.camel.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.raitonbl.hermes.smsc.camel.system.Versioned;
import com.raitonbl.hermes.smsc.config.smpp.SmppConfiguration;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SmppConnectionDefinition implements Versioned, Serializable {
    private String id;
    private String name;
    private String alias;
    private Long version;
    private String description;
    private Map<String, String> tags;
    private SmppConfiguration configuration;
}
