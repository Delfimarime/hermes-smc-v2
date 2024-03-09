package com.raitonbl.hermes.smsc.camel.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raitonbl.hermes.smsc.camel.asyncapi.ReceivedSmsRequest;
import com.raitonbl.hermes.smsc.camel.asyncapi.SmsDeliveryReceipt;
import com.raitonbl.hermes.smsc.sdk.HermesConstants;
import com.raitonbl.hermes.smsc.config.HermesConfiguration;
import com.raitonbl.hermes.smsc.sdk.HermesSystemConstants;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.smpp.SmppConstants;
import org.jsmpp.util.DeliveryReceiptState;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;

@Component
public  class SmppConnectionListenerRouterBuilder extends RouteBuilder {
    public static final String ROUTE_ID = HermesSystemConstants.SYSTEM_ROUTE_PREFIX +"PDU_LISTENER";
    public static final String DIRECT_TO = "direct:" + ROUTE_ID;
    public final static String UNSUPPORTED_PDU_EVENT = String.format(
            "PduEvent{type:${headers.%s},source:${headers.%s},id:${headers.%s}} isn't supported",
            SmppConstants.MESSAGE_TYPE, HermesConstants.MESSAGE_RECEIVED_BY, SmppConstants.ID
    );
    public final static String RECEIVED_SMS_PDU_EVENT = String.format(
            "PduEvent{type:${headers.%s},source:${headers.%s},id:${headers.%s},state:${headers.%s}} recognized as ReceivedSms",
            SmppConstants.MESSAGE_TYPE, HermesConstants.MESSAGE_RECEIVED_BY, SmppConstants.ID, SmppConstants.MESSAGE_STATE
    );
    public final static String SMS_DELIVERY_PDU_EVENT = String.format(
            "PduEvent{type:${headers.%s},source:${headers.%s},id:${headers.%s},state:${headers.%s}} recognized as SmsDelivery",
            SmppConstants.MESSAGE_TYPE, HermesConstants.MESSAGE_RECEIVED_BY, SmppConstants.ID, SmppConstants.MESSAGE_STATE
    );
    public final static String PDU_CONVERTED_INTO_RECEIVED_SMS = String.format(
            "PduEvent{type:${headers.%s},source:${headers.%s},id:${headers.%s},state:${headers.%s}} converted into ReceivedSms",
            SmppConstants.MESSAGE_TYPE, HermesConstants.MESSAGE_RECEIVED_BY, SmppConstants.ID, SmppConstants.MESSAGE_STATE
    );
    public final static String PDU_CONVERTED_INTO_DELIVERED_SMS = String.format(
            "PduEvent{type:${headers.%s},source:${headers.%s},id:${headers.%s} state:${headers.%s}} converted into SmsDelivery",
            SmppConstants.MESSAGE_TYPE, HermesConstants.MESSAGE_RECEIVED_BY, SmppConstants.ID, SmppConstants.MESSAGE_STATE
    );

    private  ObjectMapper objectMapper;
    private HermesConfiguration configuration;


    @Override
    public void configure() throws Exception {
        if (configuration.getServices() == null || configuration.getServices().isEmpty()) {
            return;
        }
        from("direct:" + ROUTE_ID)
                .routeId(ROUTE_ID)
                .routeDescription("Listens to smpp.PduEvent and submits it to an queue")
                .log(LoggingLevel.DEBUG, "smpp.PduEvent received from Smpp{\"name\":\"${headers." + HermesConstants.MESSAGE_RECEIVED_BY + "}\"}")
                .choice()
                .when(header(SmppConstants.MESSAGE_TYPE).isEqualTo("DeliverSm"))
                    .log(LoggingLevel.INFO,RECEIVED_SMS_PDU_EVENT)
                    .process(this::toSmsRequest)
                    .log(LoggingLevel.DEBUG, PDU_CONVERTED_INTO_RECEIVED_SMS)
                    .to("direct:" + PublishPduEventRouteType.RECEIVED_SMS_REQUEST_ROUTE.routeId)
                .when(header(SmppConstants.MESSAGE_TYPE).isEqualTo("DeliveryReceipt"))
                    .log(LoggingLevel.INFO, SMS_DELIVERY_PDU_EVENT)
                    .process(this::toSmsDeliveryRequest)
                    .log(LoggingLevel.DEBUG, PDU_CONVERTED_INTO_DELIVERED_SMS)
                    .to("direct:" +  PublishPduEventRouteType.DELIVERY_RECEIPT_ROUTE.routeId)
                .otherwise()
                    .log(LoggingLevel.WARN, UNSUPPORTED_PDU_EVENT)
                    .to("direct:" + PublishPduEventRouteType.UNSUPPORTED_PDU_EVENT_ROUTE.routeId)
                .end()
                .removeHeaders("*")
                .end()
        ;
    }

    private void toSmsRequest(Exchange exchange) throws Exception {
        Instant instant = Instant.ofEpochMilli(exchange.getCreated());
        ReceivedSmsRequest target = new ReceivedSmsRequest();
        target.setContent(exchange.getIn().getBody(String.class));
        target.setId(exchange.getIn().getHeader(SmppConstants.COMMAND_ID, String.class));
        target.setReceivedAt(instant.atZone(ZoneId.systemDefault()).toLocalDateTime());
        target.setDestAddr(exchange.getIn().getHeader(SmppConstants.DEST_ADDR, String.class));
        target.setSourceAddr(exchange.getIn().getHeader(SmppConstants.SOURCE_ADDR, String.class));
        target.setSmpp(exchange.getIn().getHeader(HermesConstants.MESSAGE_RECEIVED_BY, String.class));
        exchange.getIn().setBody(objectMapper.writeValueAsString(target));
    }

    private void toSmsDeliveryRequest(Exchange exchange) throws Exception {
        Instant instant = Instant.ofEpochMilli(exchange.getCreated());
        SmsDeliveryReceipt target = new SmsDeliveryReceipt();
        target.setId(exchange.getIn().getHeader(SmppConstants.ID, String.class));
        target.setReceivedAt(instant.atZone(ZoneId.systemDefault()).toLocalDateTime());
        target.setSmpp(exchange.getIn().getHeader(HermesConstants.MESSAGE_RECEIVED_BY, String.class));
        target.setStatus(exchange.getIn().getHeader(SmppConstants.FINAL_STATUS, DeliveryReceiptState.class));
        target.setLastAttemptAt(exchange.getIn().getHeader(SmppConstants.DONE_DATE, Date.class).toInstant()
                .atZone(ZoneId.systemDefault()).toLocalDateTime());
        target.setDelivered(exchange.getIn().getHeader(SmppConstants.DELIVERED, Boolean.class));
        exchange.getIn().setBody(objectMapper.writeValueAsString(target));
    }

    @Inject
    public void setObjectMapper(ObjectMapper objectMapper){
        this.objectMapper = objectMapper;
    }
    @Inject
    public void setConfiguration(HermesConfiguration configuration) {
        this.configuration = configuration;
    }

}
