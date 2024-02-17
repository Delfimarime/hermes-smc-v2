package com.raitonbl.hermes.smsc.camel;

import lombok.Builder;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Predicate;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.smpp.SmppConstants;
import org.apache.camel.component.smpp.SmppMessage;

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

    @Override
    public void configure() throws Exception {
        from("direct:" + ROUTE_ID)
                .routeId(ROUTE_ID)
                .routeDescription("Listens to smpp.PduEvent and submits it to an queue")
                .choice()
                .when(header(SmppConstants.MESSAGE_TYPE).isEqualTo("DeliverSm"))
                    .log(LoggingLevel.WARN, UNSUPPORTED_PDU_EVENT)
                    //TODO SEND TO PDU_EVENT_QUEUE
                    .removeHeaders("*")
                .otherwise()
                    .choice()
                    .when(header(SmppConstants.ESM_CLASS).isEqualTo(0x00))
                        .process(this::toSmsRequest)
                        .to("seda:specialQueue")
                    .when(header(SmppConstants.ESM_CLASS).isEqualTo(0x04))
                        .process(this::toSmsDeliveryRequest)
                        .to("seda:specialQueue")
                    .otherwise()
                        .log(LoggingLevel.WARN, UNSUPPORTED_PDU_EVENT_WITH_ESM_CLASS)
                        //TODO SEND TO PDU_EVENT_QUEUE
                    .endChoice()
                .endChoice()
                .removeHeader("*")
                .end()
        ;
    }

    public void toSmsRequest(Exchange exchange){

    }

    public void toSmsDeliveryRequest(Exchange exchange){

    }

}
