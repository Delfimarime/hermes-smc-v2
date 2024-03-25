package com.raitonbl.hermes.smsc.camel.engine.smpp;

public class CannotSendSmsRequestException extends RuntimeException {
    public CannotSendSmsRequestException() {
    }
    public CannotSendSmsRequestException(String msg) {
        super(msg);
    }

}
