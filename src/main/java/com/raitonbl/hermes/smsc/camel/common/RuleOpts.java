package com.raitonbl.hermes.smsc.camel.common;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.apache.camel.builder.ValueBuilder;

import java.util.Map;

@Getter
@Setter
@Builder
public class RuleOpts {
    private String readURI;
    private String writeURI;
    private Map<String, ValueBuilder> readHeaders;
    private Map<String, ValueBuilder> writeHeaders;
    private Class<? extends Exception> catchableReadException;
    private String onCatchReadExceptionLog;
}
