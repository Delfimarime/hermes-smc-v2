package com.raitonbl.hermes.smsc.camel.engine.smpp;

import com.raitonbl.hermes.smsc.camel.asyncapi.SendSmsRequest;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.function.Predicate;

@Getter
@Builder
public class Policy {
    private String id;
    private List<SmppConnectionInformation> target;
    private Predicate<SendSmsRequest> predicate;
    public boolean isPermitted(SendSmsRequest request) {
        return this.predicate == null ? Boolean.FALSE : this.predicate.test(request);
    }
}
