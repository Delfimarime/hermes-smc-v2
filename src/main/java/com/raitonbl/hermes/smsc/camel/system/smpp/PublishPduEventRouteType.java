package com.raitonbl.hermes.smsc.camel.system.smpp;

import com.raitonbl.hermes.smsc.camel.common.HermesSystemConstants;

public enum PublishPduEventRouteType {
    DELIVERY_RECEIPT_ROUTE(HermesSystemConstants.SYSTEM_ROUTE_PREFIX + "DELIVERY_RECEIPT_LISTENER", "DELIVERY RECEIPT"),
    RECEIVED_SMS_REQUEST_ROUTE(HermesSystemConstants.SYSTEM_ROUTE_PREFIX + "RECEIVED_SMS_LISTENER", "RECEIVED MESSAGE"),
    UNSUPPORTED_PDU_EVENT_ROUTE(HermesSystemConstants.SYSTEM_ROUTE_PREFIX + "UNSUPPORTED_PDU_LISTENER", "UNSUPPORTED PDU");

    public final String routeId;
    public final String eventType;

    PublishPduEventRouteType(String routeId, String eventType) {
        this.routeId = routeId;
        this.eventType = eventType;
    }

}
