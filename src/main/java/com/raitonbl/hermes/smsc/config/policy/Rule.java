package com.raitonbl.hermes.smsc.config.policy;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@Builder
public class Rule implements Serializable {
    private String name;
    private RuleSpec spec;
    private String description;
}
