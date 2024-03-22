package com.raitonbl.hermes.smsc.camel.engine.smpp;

import com.raitonbl.hermes.smsc.camel.engine.datasource.DatasourceClient;
import com.raitonbl.hermes.smsc.config.HermesConfiguration;
import com.raitonbl.hermes.smsc.config.PublishConfiguration;
import com.raitonbl.hermes.smsc.config.messaging.MessagingSystem;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(value = DatasourceClient.class)
public class PublishPduEventRouteBuilder extends RouteBuilder {

    private HermesConfiguration configuration;

    @Override
    public void configure() {
        setRoute(PublishPduEventRouteType.DELIVERY_RECEIPT_ROUTE,
                configuration.getPublishTo(), configuration.getPublishTo().getDeliveryReceiptChannel());
        setRoute(PublishPduEventRouteType.RECEIVED_SMS_REQUEST_ROUTE,
                configuration.getPublishTo(), configuration.getPublishTo().getReceivedSmsChannel());
        setRoute(PublishPduEventRouteType.UNSUPPORTED_PDU_EVENT_ROUTE,
                configuration.getPublishTo(), configuration.getPublishTo().getUnsupportedPduChannel());
    }

    private void setRoute(PublishPduEventRouteType routeType, PublishConfiguration configuration, MessagingSystem ms) {
        from("direct:" + routeType.routeId)
                .routeId(routeType.routeId)
                .routeDescription(String.format("Publishes %s events into %s", routeType.eventType,
                        configuration.getType()).toLowerCase())
                .removeHeaders("*")
                .log(LoggingLevel.DEBUG,"Submitting "+routeType.eventType+" into Channel{\"type\":\""+configuration.getType()+"\"} ")
                .to(configuration.toCamelURI(ms))
                .removeHeaders("*")
                .end();
    }

    @Autowired
    public void setConfiguration(HermesConfiguration configuration) {
        this.configuration = configuration;
    }

}
