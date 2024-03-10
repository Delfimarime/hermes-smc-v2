package com.raitonbl.hermes.smsc.config.policy;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@Builder
public class RuleSpec  implements Serializable {
    private String from;
    private TagCriteria[] tags;
    @JsonProperty("destination")
    private String destinationAddr;
    private String smpp;
}
