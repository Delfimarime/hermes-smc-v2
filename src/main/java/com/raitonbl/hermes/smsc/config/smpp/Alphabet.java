package com.raitonbl.hermes.smsc.config.smpp;

public enum Alphabet {
    DEFAULT_ALPHABET_4((byte) 0), EIGHT_BIT_ALPHABET((byte) 4), UCS2_ALPHABET((byte) 8);

    private final byte value;

    private Alphabet(byte value) {
        this.value = value;
    }

    public byte getValue() {
        return this.value;
    }

}
