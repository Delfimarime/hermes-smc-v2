package com.raitonbl.hermes.smsc.config;

import com.raitonbl.hermes.smsc.config.common.ConfigurationUtils;
import com.raitonbl.hermes.smsc.config.messaging.MessageSystemType;
import com.raitonbl.hermes.smsc.config.messaging.MessagingSystem;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SendSmsListenerConfiguration extends MessagingSystem {
    @NotNull
    private MessageSystemType type;
    private Long visibilityTimeout;
    private Integer maxMessagesPerPoll;
    private Integer concurrentConsumers;
    private Integer receiveMessageWaitTimeSeconds;

    public String toCamelURI() {
        return MessagingSystem.camelURIFrom(type, this, (sb, isFirst) -> {
            ConfigurationUtils.setParameter(sb, isFirst, "visibilityTimeout", this.visibilityTimeout);
            ConfigurationUtils.setParameter(sb, isFirst, "maxMessagesPerPoll", this.maxMessagesPerPoll);
            ConfigurationUtils.setParameter(sb, isFirst, "concurrentConsumers", this.concurrentConsumers);
            ConfigurationUtils.setParameter(sb, isFirst, "receiveMessageWaitTimeSeconds", this.receiveMessageWaitTimeSeconds);

        });
    }
}
