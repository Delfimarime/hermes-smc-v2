package com.raitonbl.hermes.smsc.camel.system;

public class SmppConnectionNotFoundException extends RuntimeException {
    public SmppConnectionNotFoundException() {
    }

    public SmppConnectionNotFoundException(String message) {
        super(message);
    }
}
