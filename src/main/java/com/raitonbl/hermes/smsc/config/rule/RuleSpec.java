package com.raitonbl.hermes.smsc.config.rule;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RuleSpec {
    private String from;
    private TagsCriteria[] tags;
    private String destinationAddr;
}
