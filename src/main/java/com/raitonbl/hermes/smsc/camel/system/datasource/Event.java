package com.raitonbl.hermes.smsc.camel.system.datasource;

import com.raitonbl.hermes.smsc.camel.model.Entity;
import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Event implements Serializable {
    private Entity target;
    private EventType type;
    public enum EventType {
        SET, DELETE;
    }

}
