package com.raitonbl.hermes.smsc.camel.engine.datasource;

import com.raitonbl.hermes.smsc.camel.common.RecordType;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.ProcessorDefinition;

public interface EntityLifecycleListenerRouteFactory {
    ProcessorDefinition<?> create(RouteBuilder builder, RecordType dbType);

}
