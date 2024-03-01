package com.raitonbl.hermes.smsc.config.rule;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
public class TagsCriteria  implements Serializable {
    private String[] anyOf;
    private String[] allOf;
}
