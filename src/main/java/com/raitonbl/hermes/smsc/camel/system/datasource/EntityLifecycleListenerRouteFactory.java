package com.raitonbl.hermes.smsc.camel.system.datasource;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.ProcessorDefinition;

public interface EntityLifecycleListenerRouteFactory {
    ProcessorDefinition<?> create(RouteBuilder builder, DatasourceType dbType);

}
