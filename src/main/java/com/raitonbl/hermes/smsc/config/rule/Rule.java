package com.raitonbl.hermes.smsc.config.rule;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Rule {
    private String id;
    private String name;
    private String description;
    private RuleSpec spec;
}
