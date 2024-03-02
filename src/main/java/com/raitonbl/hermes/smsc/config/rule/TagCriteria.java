package com.raitonbl.hermes.smsc.config.rule;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
public class TagCriteria implements Serializable {
    private String[] anyOf;
    private String[] allOf;
}
