package com.raitonbl.hermes.smsc.camel.engine.policy;

import com.raitonbl.hermes.smsc.camel.asyncapi.SendSmsRequest;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.function.Predicate;

@Data
@Builder
public class Policy {
    private String id;
    private List<SmppTarget> target;
    private Predicate<SendSmsRequest> predicate;
    public boolean isPermitted(SendSmsRequest request) {
        return this.predicate == null ? Boolean.FALSE : this.predicate.test(request);
    }
}
