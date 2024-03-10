package com.raitonbl.hermes.smsc.config.policy;

public class CannotSendSmsRequestException extends RuntimeException {
    public CannotSendSmsRequestException() {
    }
    public CannotSendSmsRequestException(String msg) {
        super(msg);
    }

}
