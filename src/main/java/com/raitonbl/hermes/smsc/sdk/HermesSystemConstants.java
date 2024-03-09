package com.raitonbl.hermes.smsc.sdk;

public interface HermesSystemConstants {
    String ROUTE_PREFIX = "HERMES_SMSC";
    String SYSTEM_ROUTE_PREFIX = ROUTE_PREFIX + "_SYS_";
    String INTERNAL_ROUTE_PREFIX = ROUTE_PREFIX + "_INTERNAL_";

    // REPOSITORY ROUTES
    String GET_ALL_SMPP_CONNECTIONS_ROUTE = INTERNAL_ROUTE_PREFIX + "_SMPP_CONNECTION_REPOSITORY_GET_ALL";
}
