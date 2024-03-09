package com.raitonbl.hermes.smsc.sdk;

public interface HermesConstants {
    String HEADER_PREFIX = "CamelHermes";
    String SEND_REQUEST_ID = HEADER_PREFIX + "SmppId";
    String MESSAGE_RECEIVED_BY = HEADER_PREFIX + "SmppFrom";
    String SEND_SMS_PATH = HEADER_PREFIX + "SendSmsRequestPath";
    String POLICY = HEADER_PREFIX + "SendRequestPolicy";
    String SMPP_CONNECTION = HEADER_PREFIX + "SmppConnection";
    String POLICIES = HEADER_PREFIX + "SendRequestPolicies";
    String SMPP_CONNECTION_ITERATOR =SMPP_CONNECTION+"_ITERATOR";

    String REPOSITORY_RETURN_OBJECT = HEADER_PREFIX + "RepositoryReturnObject";


}
