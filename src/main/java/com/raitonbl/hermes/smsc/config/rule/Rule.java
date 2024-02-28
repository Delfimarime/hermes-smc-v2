package com.raitonbl.hermes.smsc.config.rule;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
public class Rule implements Serializable {
    private String name;
    private RuleSpec spec;
    private String description;
}
