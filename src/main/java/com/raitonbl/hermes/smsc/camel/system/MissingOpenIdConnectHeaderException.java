package com.raitonbl.hermes.smsc.camel.system;

public class MissingOpenIdConnectHeaderException extends OpenIdConnectException{
    public MissingOpenIdConnectHeaderException() {
    }

    public MissingOpenIdConnectHeaderException(String message) {
        super(message);
    }
}
