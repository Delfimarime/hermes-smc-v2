package com.raitonbl.hermes.smsc.config.rule;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TagsCriteria {
    private String anyOf;
    private String allOf;
}
