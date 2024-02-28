package com.raitonbl.hermes.smsc.config.rule;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
public class RuleSpec  implements Serializable {
    private String from;
    private TagsCriteria[] tags;
    @JsonProperty("destination")
    private String destinationAddr;
}
