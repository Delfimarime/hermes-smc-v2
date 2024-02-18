package com.raitonbl.hermes.smsc.camel;

public enum MessagingRouteType {
    DELIVERY_RECEIPT_ROUTE("HERMES_SMSC_DELIVERY_RECEIPT_LISTENER","DELIVERY RECEIPT"),
    RECEIVED_SMS_REQUEST_ROUTE("HERMES_SMSC_RECEIVED_SMS_LISTENER","RECEIVED MESSAGE"),
    UNSUPPORTED_PDU_EVENT_ROUTE("HERMES_SMSC_UNSUPPORTED_PDU_LISTENER","UNSUPPORTED PDU");

    public final String routeId;
    public final String eventType;

    MessagingRouteType(String routeId,String eventType) {
        this.routeId = routeId;
        this.eventType = eventType;
    }

}
