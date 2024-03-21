package com.raitonbl.hermes.smsc.camel.system.smpp;

import com.raitonbl.hermes.smsc.camel.system.datasource.EntityNotFoundException;

public class SmppConnectionNotFoundException extends EntityNotFoundException {
    public SmppConnectionNotFoundException() {
    }

    public SmppConnectionNotFoundException(String message) {
        super(message);
    }
}
