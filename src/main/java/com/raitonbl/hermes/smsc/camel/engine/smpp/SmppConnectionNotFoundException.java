package com.raitonbl.hermes.smsc.camel.engine.smpp;

import com.raitonbl.hermes.smsc.camel.engine.datasource.EntityNotFoundException;

public class SmppConnectionNotFoundException extends EntityNotFoundException {
    public SmppConnectionNotFoundException() {
    }

    public SmppConnectionNotFoundException(String message) {
        super(message);
    }
}
