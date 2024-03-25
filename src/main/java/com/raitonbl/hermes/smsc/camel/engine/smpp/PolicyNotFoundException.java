package com.raitonbl.hermes.smsc.camel.engine.smpp;

import com.raitonbl.hermes.smsc.camel.engine.datasource.EntityNotFoundException;

public class PolicyNotFoundException extends EntityNotFoundException {
    public PolicyNotFoundException() {
    }

    public PolicyNotFoundException(String message) {
        super(message);
    }
}
