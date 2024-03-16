package com.raitonbl.hermes.smsc.camel.common;

public interface HermesConstants {
    String HEADER_PREFIX = "CamelHermes";
    String SEND_SMS_REQUEST_ID = HEADER_PREFIX + "SmppId";
    String MESSAGE_RECEIVED_BY = HEADER_PREFIX + "SmppFrom";
    String POLICY = HEADER_PREFIX + "SendRequestPolicy";
    String SMPP_CONNECTION = HEADER_PREFIX + "SmppConnection";
    String POLICIES = HEADER_PREFIX + "SendRequestPolicies";
    String SMPP_CONNECTION_ITERATOR = SMPP_CONNECTION + "Iterator";

    String REPOSITORY_RETURN_OBJECT = HEADER_PREFIX + "RepositoryReturnObject";
    String AUTHORIZATION_TOKEN = "Authorization";
    String AUTHORIZATION = HEADER_PREFIX + AUTHORIZATION_TOKEN;


}
