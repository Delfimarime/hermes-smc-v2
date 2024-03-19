package com.raitonbl.hermes.smsc.camel.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class Entity {
    @JsonProperty("id")
    private String id;
    @JsonProperty("version")
    private Long version;
}
