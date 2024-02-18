package com.raitonbl.hermes.smsc.camel;

import com.raitonbl.hermes.smsc.config.HermesConfiguration;
import jakarta.inject.Inject;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

@Component
public class SendSmsRequestRouteBuilder extends RouteBuilder {

    private HermesConfiguration configuration;

    @Override
    public void configure() throws Exception {
        from(configuration.getListenTo().toCamelURI());
    }

    @Inject
    public void setConfiguration(HermesConfiguration configuration) {
        this.configuration = configuration;
    }
}
