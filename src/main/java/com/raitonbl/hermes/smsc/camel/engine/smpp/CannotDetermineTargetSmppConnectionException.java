package com.raitonbl.hermes.smsc.camel.engine.smpp;

public class CannotDetermineTargetSmppConnectionException extends CannotSendSmsRequestException {
    public CannotDetermineTargetSmppConnectionException() {
    }
    public CannotDetermineTargetSmppConnectionException(String msg) {
        super(msg);
    }

}
