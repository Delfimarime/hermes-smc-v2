package com.raitonbl.hermes.smsc.camel.system.common;

import com.raitonbl.hermes.smsc.camel.system.common.OpenIdConnectException;

public class MissingOpenIdConnectHeaderException extends OpenIdConnectException {
    public MissingOpenIdConnectHeaderException() {
    }

    public MissingOpenIdConnectHeaderException(String message) {
        super(message);
    }
}
