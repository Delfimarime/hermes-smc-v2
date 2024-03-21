package com.raitonbl.hermes.smsc.camel.system.security;

public class OpenIdConnectException extends RuntimeException{
    public OpenIdConnectException() {
    }

    public OpenIdConnectException(Throwable cause) {
        super(cause);
    }

    public OpenIdConnectException(String message) {
        super(message);
    }
}
