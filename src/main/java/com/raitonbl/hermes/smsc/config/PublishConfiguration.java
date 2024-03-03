package com.raitonbl.hermes.smsc.config;

import com.raitonbl.hermes.smsc.config.messaging.MessageSystemType;
import com.raitonbl.hermes.smsc.config.messaging.MessagingSystem;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PublishConfiguration {
    @NotNull
    private MessageSystemType type;
    @NotNull
    private MessagingSystem receivedSmsChannel;
    @NotNull
    private MessagingSystem unsupportedPduChannel;
    @NotNull
    private MessagingSystem deliveryReceiptChannel;

    public String toCamelURI(MessagingSystem target) {
        return MessagingSystem.camelURIFrom(type, target);
    }
}
