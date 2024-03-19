package com.raitonbl.hermes.smsc.camel.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.raitonbl.hermes.smsc.config.smpp.SmppConfiguration;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.util.Map;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SmppConnectionDefinition extends Entity implements Serializable {
    @JsonProperty("name")
    private String name;
    @JsonProperty("alias")
    private String alias;
    @JsonProperty("description")
    private String description;
    @JsonProperty("tags")
    private Map<String, String> tags;
    @JsonProperty("spec")
    private SmppConfiguration configuration;

    @Builder
    public SmppConnectionDefinition(String id, Long version, String name, String alias, String description, Map<String, String> tags, SmppConfiguration configuration) {
        super(id, version);
        this.name = name;
        this.alias = alias;
        this.description = description;
        this.tags = tags;
        this.configuration = configuration;
    }
}
