package com.raitonbl.hermes.smsc.config.smpp;

public enum RegisteredDelivery {
    NO_DELIVERY, ALWAYS, ON_FAILURE;

    public byte getValue() {
        return (byte) this.ordinal();
    }

}
