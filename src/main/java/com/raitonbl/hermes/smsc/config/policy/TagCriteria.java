package com.raitonbl.hermes.smsc.config.policy;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@Builder
public class TagCriteria implements Serializable {
    private String[] anyOf;
    private String[] allOf;
}
