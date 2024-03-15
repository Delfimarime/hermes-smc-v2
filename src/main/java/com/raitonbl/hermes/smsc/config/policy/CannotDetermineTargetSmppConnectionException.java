package com.raitonbl.hermes.smsc.config.policy;

public class CannotDetermineTargetSmppConnectionException extends CannotSendSmsRequestException {
    public CannotDetermineTargetSmppConnectionException() {
    }
    public CannotDetermineTargetSmppConnectionException(String msg) {
        super(msg);
    }

}
