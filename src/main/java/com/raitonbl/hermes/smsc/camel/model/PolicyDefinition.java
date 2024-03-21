package com.raitonbl.hermes.smsc.camel.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PolicyDefinition extends Entity implements Serializable {

    @JsonProperty("name")
    private String name;
    @JsonProperty("description")
    private String description;
    @JsonProperty("spec")
    private Spec spec;

    public PolicyDefinition(String id, String name, String description, Spec spec) {
        this(id, name, description, spec, null);
    }

    @Builder
    public PolicyDefinition(String id, String name, String description, Spec spec, Long version) {
        super(id, version);
        this.name = name;
        this.description = description;
        this.spec = spec;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Spec implements Serializable {
        @JsonProperty("from")
        private String from;
        @JsonProperty("destination")
        private String destination;
        @JsonProperty("resource")
        private List<ResourceDefinition> resources;
        @JsonProperty("tags")
        private Map<String, String> tags;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ResourceDefinition implements Serializable {
        @JsonProperty("id")
        private String id;
        @JsonProperty("tags")
        private Map<String, String> tags;
    }
}