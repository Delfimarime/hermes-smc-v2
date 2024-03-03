package com.raitonbl.hermes.smsc.camel;

import com.raitonbl.hermes.smsc.config.HermesConfiguration;
import com.raitonbl.hermes.smsc.config.PublishConfiguration;
import com.raitonbl.hermes.smsc.config.messaging.MessagingSystem;
import lombok.Setter;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Setter
@Component
public class MessagingRouteBuilder extends RouteBuilder {

    HermesConfiguration configuration;

    @Override
    public void configure() {
        if (configuration.getServices() == null || configuration.getServices().isEmpty()) {
            return;
        }
        setRoute(MessagingRouteType.DELIVERY_RECEIPT_ROUTE,
                configuration.getPublishTo(), configuration.getPublishTo().getDeliveryReceiptChannel());
        setRoute(MessagingRouteType.RECEIVED_SMS_REQUEST_ROUTE,
                configuration.getPublishTo(), configuration.getPublishTo().getReceivedSmsChannel());
        setRoute(MessagingRouteType.UNSUPPORTED_PDU_EVENT_ROUTE,
                configuration.getPublishTo(), configuration.getPublishTo().getUnsupportedPduChannel());
    }

    private void setRoute(MessagingRouteType routeType, PublishConfiguration configuration, MessagingSystem ms) {
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
