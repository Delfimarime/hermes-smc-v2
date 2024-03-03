package com.raitonbl.hermes.smsc.config.rule;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.function.Consumer;

@Getter
@Setter
@Builder
public class Rule implements Serializable {
    private String name;
    private RuleSpec spec;
    private String description;
}
