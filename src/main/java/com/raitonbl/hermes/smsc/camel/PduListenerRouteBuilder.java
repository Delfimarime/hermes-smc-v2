package com.raitonbl.hermes.smsc.camel;

import lombok.Builder;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.smpp.SmppConstants;

@Builder
public abstract class PduListenerRouteBuilder extends RouteBuilder {
    public static final String ROUTE_ID = "HERMES_SMSC_PDU_LISTENER";
    public static final String DIRECT_TO = "direct:" + ROUTE_ID;
    public final static String UNSUPPORTED_PDU_EVENT = String.format(
            "PduEvent{type:${headers.%s} , source:${headers.%s},id:${headers.%s}, state:${headers.%s} } isn't supported",
            SmppConstants.MESSAGE_TYPE, Constants.MESSAGE_RECEIVED_BY, SmppConstants.ID, SmppConstants.MESSAGE_STATE
    );
    public final static String UNSUPPORTED_PDU_EVENT_WITH_ESM_CLASS = String.format(
            "PduEvent{type:${headers.%s} , source:${headers.%s},id:${headers.%s}, state:${headers.%s}, esmClass:${headers.%s} } isn't supported",
            SmppConstants.MESSAGE_TYPE, Constants.MESSAGE_RECEIVED_BY, SmppConstants.ID, SmppConstants.MESSAGE_STATE,SmppConstants.ESM_CLASS
    );
    public final static String RECEIVED_SMS_PDU_EVENT = String.format(
            "PduEvent{type:${headers.%s} , source:${headers.%s},id:${headers.%s}, state:${headers.%s}, esmClass:${headers.%s} } recognized as ReceivedSms",
            SmppConstants.MESSAGE_TYPE, Constants.MESSAGE_RECEIVED_BY, SmppConstants.ID, SmppConstants.MESSAGE_STATE,SmppConstants.ESM_CLASS
    );
    public final static String SMS_DELIVERY_PDU_EVENT = String.format(
            "PduEvent{type:${headers.%s} , source:${headers.%s},id:${headers.%s}, state:${headers.%s}, esmClass:${headers.%s} } recognized as SmsDelivery",
            SmppConstants.MESSAGE_TYPE, Constants.MESSAGE_RECEIVED_BY, SmppConstants.ID, SmppConstants.MESSAGE_STATE,SmppConstants.ESM_CLASS
    );
    public final static String PDU_CONVERTED_INTO_RECEIVED_SMS = String.format(
            "PduEvent{type:${headers.%s} , source:${headers.%s},id:${headers.%s}, state:${headers.%s}, esmClass:${headers.%s} } converted into ReceivedSms",
            SmppConstants.MESSAGE_TYPE, Constants.MESSAGE_RECEIVED_BY, SmppConstants.ID, SmppConstants.MESSAGE_STATE,SmppConstants.ESM_CLASS
    );
    public final static String PDU_CONVERTED_INTO_DELIVERED_SMS = String.format(
            "PduEvent{type:${headers.%s} , source:${headers.%s},id:${headers.%s}, state:${headers.%s}, esmClass:${headers.%s} } converted into SmsDelivery",
            SmppConstants.MESSAGE_TYPE, Constants.MESSAGE_RECEIVED_BY, SmppConstants.ID, SmppConstants.MESSAGE_STATE,SmppConstants.ESM_CLASS
    );

    @Override
    public void configure() throws Exception {
        from("direct:" + ROUTE_ID)
                .routeId(ROUTE_ID)
                .routeDescription("Listens to smpp.PduEvent and submits it to an queue")
                .choice()
                .when(header(SmppConstants.MESSAGE_TYPE).isEqualTo("DeliverSm"))
                    .log(LoggingLevel.WARN, UNSUPPORTED_PDU_EVENT)
                    .to("direct:" + UnsupportedPduEventRouteBuilder.ROUTE_ID)
                    .removeHeaders("*")
                .otherwise()
                    .choice()
                    .when(header(SmppConstants.ESM_CLASS).isEqualTo(0x00))
                        .log(LoggingLevel.INFO,RECEIVED_SMS_PDU_EVENT)
                        .process(this::toSmsRequest)
                        .log(LoggingLevel.DEBUG, PDU_CONVERTED_INTO_RECEIVED_SMS)
                        .removeHeaders("*")
                        .to("direct:" + ReceiveSmsPduEventRouteBuilder.ROUTE_ID)
                    .when(header(SmppConstants.ESM_CLASS).isEqualTo(0x04))
                        .log(LoggingLevel.INFO,RECEIVED_SMS_PDU_EVENT)
                        .process(this::toSmsDeliveryRequest)
                        .log(LoggingLevel.DEBUG, PDU_CONVERTED_INTO_DELIVERED_SMS)
                        .removeHeaders("*")
                        .to("direct:" + ReceiveSmsPduEventRouteBuilder.ROUTE_ID)
                    .otherwise()
                        .log(LoggingLevel.WARN, UNSUPPORTED_PDU_EVENT_WITH_ESM_CLASS)
                        .to("direct:" + UnsupportedPduEventRouteBuilder.ROUTE_ID)
                    .endChoice()
                .endChoice()
                .removeHeaders("*")
                .end()
        ;
    }

    public void toSmsRequest(Exchange exchange){

    }

    public void toSmsDeliveryRequest(Exchange exchange){

    }

}
