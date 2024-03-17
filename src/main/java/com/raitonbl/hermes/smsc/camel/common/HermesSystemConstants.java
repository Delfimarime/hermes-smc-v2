package com.raitonbl.hermes.smsc.camel.common;


public interface HermesSystemConstants {
    // COMMON(S)
    String KV_CACHE_NAME = "kvCache";
    String ROUTE_PREFIX = "HERMES_SMSC";
    String RESTAPI_ROUTE_PREFIX = ROUTE_PREFIX + "_RESTAPI_";
    String ASYNCAPI_ROUTE_PREFIX = ROUTE_PREFIX + "_ASYNCAPI_";
    String SYSTEM_ROUTE_PREFIX = ROUTE_PREFIX + "_SYS_";
    String INTERNAL_ROUTE_PREFIX = ROUTE_PREFIX + "_INTERNAL_";

    // SEND SMS REQUEST ROUTE(S)
    String SEND_SMS_REQUEST_ROUTE_PREFIX = "SEND_MESSAGE";
    String SEND_MESSAGE_SYSTEM_ROUTE = SYSTEM_ROUTE_PREFIX + SEND_SMS_REQUEST_ROUTE_PREFIX;
    String DIRECT_TO_SEND_MESSAGE_SYSTEM_ROUTE = "direct:" + SEND_MESSAGE_SYSTEM_ROUTE;
    String SEND_MESSAGE_THROUGH_HTTP_INTERFACE = RESTAPI_ROUTE_PREFIX + SEND_SMS_REQUEST_ROUTE_PREFIX;
    String SEND_MESSAGE_THROUGH_ASYNCAPI_INTERFACE = ASYNCAPI_ROUTE_PREFIX + SEND_SMS_REQUEST_ROUTE_PREFIX;

    // POLICY ROUTE(S)
    String UPDATE_POLICIES_RESTAPI_ROUTE = HermesSystemConstants.RESTAPI_ROUTE_PREFIX + "POLICIES_PUT";
    String GET_ALL_POLICIES_RESTAPI_ROUTE = HermesSystemConstants.RESTAPI_ROUTE_PREFIX + "POLICIES_GET";

    // SMPP DECIDER ROUTE(S)
    String SMPP_DECIDER_SYSTEM_ROUTE = SYSTEM_ROUTE_PREFIX + "GET_TARGET_FROM_POLICIES";
    String DIRECT_TO_SMPP_DECIDER_SYSTEM_ROUTE = "direct:" + SMPP_DECIDER_SYSTEM_ROUTE;

    // REPOSITORY ROUTE(S)
    String SMPP_REPOSITORY_ROUTE_PREFIX = SYSTEM_ROUTE_PREFIX + "SMPP_CONNECTION_REPOSITORY";
    String GET_ALL_SMPP_CONNECTIONS_ROUTE = SMPP_REPOSITORY_ROUTE_PREFIX + "_GET_ALL";
    String DIRECT_TO_GET_ALL_SMPP_CONNECTIONS_ROUTE = "direct:" + GET_ALL_SMPP_CONNECTIONS_ROUTE;
    String FIND_SMPP_CONNECTION_BY_ID = GET_ALL_SMPP_CONNECTIONS_ROUTE + "%s_FIND_BY_ID";
    String DIRECT_TO_FIND_SMPP_CONNECTION_BY_ID = "direct:" + FIND_SMPP_CONNECTION_BY_ID;

    // POLICY ROUTES(S)
    String READ_POLICIES_FROM_DATASOURCE_SYSTEM_ROUTE = SYSTEM_ROUTE_PREFIX + "POLICY_READ";
    String DIRECT_TO_READ_POLICIES_FROM_DATASOURCE_SYSTEM_ROUTE = "direct:" + READ_POLICIES_FROM_DATASOURCE_SYSTEM_ROUTE;
    String UPDATE_POLICIES_ON_DATASOURCE_ROUTE = SYSTEM_ROUTE_PREFIX + "POLICY_UPDATE";
    String DIRECT_TO_UPDATE_POLICIES_ON_DATASOURCE_ROUTE = "direct:" + UPDATE_POLICIES_ON_DATASOURCE_ROUTE;

    // SMPP CONNECTION ROUTE(S)
    String SMPP_CONNECTION_RECEIVER_ROUTE_ID_FORMAT = SYSTEM_ROUTE_PREFIX + "%s_RECEIVER_CONNECTION";
    String SMPP_CONNECTION_TRANSMITTER_ROUTE_ID_FORMAT = SYSTEM_ROUTE_PREFIX + "%s_TRANSMITTER_CONNECTION";
    String DIRECT_TO_SMPP_CONNECTION_TRANSMITTER_ROUTE_ID_FORMAT = "direct:" + SMPP_CONNECTION_TRANSMITTER_ROUTE_ID_FORMAT;

    // OPENID CONNECT
    String OPENID_CONNECT_GET_AUTHENTICATION = SYSTEM_ROUTE_PREFIX + "OPENID_CONNECT_GET_AUTHENTICATION";
    String DIRECT_TO_OPENID_CONNECT_GET_AUTHENTICATION = "direct:" + OPENID_CONNECT_GET_AUTHENTICATION;

    // NEW REPOSITORY
    String REPOSITORY_FIND_ALL = INTERNAL_ROUTE_PREFIX + "REPOSITORY_FIND_ALL";
    String DIRECT_TO_REPOSITORY_FIND_ALL = "direct:" + REPOSITORY_FIND_ALL;

    String REPOSITORY_FIND_BY_ID = INTERNAL_ROUTE_PREFIX + "REPOSITORY_FIND_BY_ID";
    String DIRECT_TO_REPOSITORY_FIND_BY_ID = "direct:" + REPOSITORY_FIND_BY_ID;

    String REPOSITORY_SET_BY_ID = INTERNAL_ROUTE_PREFIX + "REPOSITORY_EDIT_BY_ID";
    String DIRECT_TO_REPOSITORY_SET_BY_ID = "direct:" + REPOSITORY_SET_BY_ID;

}
