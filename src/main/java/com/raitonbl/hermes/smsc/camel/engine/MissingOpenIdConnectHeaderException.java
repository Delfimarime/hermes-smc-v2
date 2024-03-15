package com.raitonbl.hermes.smsc.camel.engine;

public class MissingOpenIdConnectHeaderException extends OpenIdConnectException{
    public MissingOpenIdConnectHeaderException() {
    }

    public MissingOpenIdConnectHeaderException(String message) {
        super(message);
    }
}
