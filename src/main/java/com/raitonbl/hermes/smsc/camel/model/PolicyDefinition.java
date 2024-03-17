package com.raitonbl.hermes.smsc.camel.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.raitonbl.hermes.smsc.camel.system.Versioned;
import lombok.*;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PolicyDefinition implements Versioned,Serializable {
    @JsonProperty("id")
    private String id;
    @JsonProperty("version")
    private Long version;
    @JsonProperty("spec")
    private Spec spec;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Spec  implements Serializable{
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
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ResourceDefinition  implements Serializable{
        @JsonProperty("id")
        private String id;
        @JsonProperty("tags")
        private Map<String, String> tags;
    }
}