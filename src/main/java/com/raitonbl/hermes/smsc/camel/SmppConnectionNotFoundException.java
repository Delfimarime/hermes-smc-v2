package com.raitonbl.hermes.smsc.camel;

public class SmppConnectionNotFoundException extends RuntimeException {
    public SmppConnectionNotFoundException() {
    }

    public SmppConnectionNotFoundException(String message) {
        super(message);
    }
}
