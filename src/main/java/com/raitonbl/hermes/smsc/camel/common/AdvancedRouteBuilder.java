package com.raitonbl.hermes.smsc.camel.common;

import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.ValueBuilder;

import java.util.Map;

public abstract class AdvancedRouteBuilder extends RouteBuilder {
    private Processor addHeader(Map<String, ValueBuilder> h) {
        return (exchange -> h.forEach((k, v) -> exchange.getIn().setHeader(k, v)));
    }

    private Processor removeHeader(Map<String, ValueBuilder> h) {
        return (exchange -> h.forEach((k, v) -> exchange.getIn().removeHeader(k)));
    }
}
