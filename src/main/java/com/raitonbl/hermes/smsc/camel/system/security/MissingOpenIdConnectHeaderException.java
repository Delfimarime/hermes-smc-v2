package com.raitonbl.hermes.smsc.camel.system.security;

public class MissingOpenIdConnectHeaderException extends OpenIdConnectException {
    public MissingOpenIdConnectHeaderException() {
    }

    public MissingOpenIdConnectHeaderException(String message) {
        super(message);
    }
}
