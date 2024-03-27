package com.raitonbl.hermes.smsc.camel.engine.common;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

@Component
public class IntegrationRouteBuilder extends RouteBuilder {
    public static final String PROPERTIES_PATTERN = "\\$\\{properties\\.(\\w+)\\}";
    public static final String ENVIRONMENT_PATTERN = "\\$\\{os\\.environment\\.(\\w+)\\}";
    public static final String INTEGRATION_PATTERN = "\\$\\{integrations\\.(\\w+)\\.(\\w+)\\}";

    @Override
    public void configure() throws Exception {

    }
}
