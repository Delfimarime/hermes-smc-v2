package com.raitonbl.hermes.smsc.camel.system;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.ProcessorDefinition;

public interface EntityListenerRouteFactory {
    ProcessorDefinition<?> create(RouteBuilder builder, DbType dbType);

}
