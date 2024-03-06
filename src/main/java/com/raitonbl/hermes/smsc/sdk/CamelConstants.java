package com.raitonbl.hermes.smsc.sdk;

public interface CamelConstants {
    String ROUTE_PREFIX = "HERMES_SMSC";
    String SYSTEM_ROUTE_PREFIX = ROUTE_PREFIX + "_SYS_";
    String HEADER_PREFIX = "CamelHermes";
    String SEND_REQUEST_ID = HEADER_PREFIX + "SmppId";
    String MESSAGE_RECEIVED_BY = "CamelHermesSmppFrom";
    String SEND_SMS_PATH_HEADER = HEADER_PREFIX + "SendSmsRequestPath";
}
