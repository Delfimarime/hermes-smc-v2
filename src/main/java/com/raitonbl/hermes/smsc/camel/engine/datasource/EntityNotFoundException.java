package com.raitonbl.hermes.smsc.camel.engine.datasource;

public class EntityNotFoundException extends RuntimeException{
    public EntityNotFoundException() {
    }

    public EntityNotFoundException(String message) {
        super(message);
    }
}
