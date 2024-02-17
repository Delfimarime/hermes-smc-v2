package com.raitonbl.hermes.smsc.camel;

import org.apache.camel.builder.RouteBuilder;

public class MessagingRouteBuilder extends RouteBuilder {
    public static final String DELIVERY_RECEIPT_ROUTE = "HERMES_SMSC_DELIVERY_RECEIPT_LISTENER";
    public static final String RECEIVED_SMS_REQUEST_ROUTE = "HERMES_SMSC_RECEIVED_SMS_LISTENER";
    public static final String UNSUPPORTED_PDU_EVENT_ROUTE = "HERMES_SMSC_UNSUPPORTED_PDU_LISTENER";

    @Override
    public void configure() throws Exception {

    }

}
