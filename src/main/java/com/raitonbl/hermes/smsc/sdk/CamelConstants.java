package com.raitonbl.hermes.smsc.sdk;

public interface CamelConstants {
    String ROUTE_PREFIX = "HERMES_SMSC";
    String SYSTEM_ROUTE_PREFIX = ROUTE_PREFIX + "_SYS_";
    String HEADER_PREFIX="Hermes";

    String SEND_REQUEST_ID = "CamelHermesSmppId";
    String MESSAGE_RECEIVED_BY = "CamelHermesSmppFrom";
}
