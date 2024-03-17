package com.raitonbl.hermes.smsc.camel.system;

public interface Versioned {
    String getId();
    void setId(String id);
    Long getVersion();
    void setVersion(Long version);

}
