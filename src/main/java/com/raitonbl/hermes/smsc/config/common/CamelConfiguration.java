package com.raitonbl.hermes.smsc.config.common;

import org.apache.camel.Exchange;

import java.io.Serializable;

public interface CamelConfiguration extends Serializable {
    String toCamelURI();

    default void setHeaders(Exchange exchange) {
    }
}
