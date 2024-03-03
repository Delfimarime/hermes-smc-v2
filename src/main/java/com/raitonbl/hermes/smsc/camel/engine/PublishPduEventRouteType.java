package com.raitonbl.hermes.smsc.camel.engine;

import com.raitonbl.hermes.smsc.sdk.CamelConstants;

public enum PublishPduEventRouteType {
    DELIVERY_RECEIPT_ROUTE(CamelConstants.SYSTEM_ROUTE_PREFIX + "DELIVERY_RECEIPT_LISTENER", "DELIVERY RECEIPT"),
    RECEIVED_SMS_REQUEST_ROUTE(CamelConstants.SYSTEM_ROUTE_PREFIX + "RECEIVED_SMS_LISTENER", "RECEIVED MESSAGE"),
    UNSUPPORTED_PDU_EVENT_ROUTE(CamelConstants.SYSTEM_ROUTE_PREFIX + "UNSUPPORTED_PDU_LISTENER", "UNSUPPORTED PDU");

    public final String routeId;
    public final String eventType;

    PublishPduEventRouteType(String routeId, String eventType) {
        this.routeId = routeId;
        this.eventType = eventType;
    }

}
