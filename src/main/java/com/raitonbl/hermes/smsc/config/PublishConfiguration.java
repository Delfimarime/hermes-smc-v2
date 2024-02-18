package com.raitonbl.hermes.smsc.config;

import com.raitonbl.hermes.smsc.config.common.ConfigurationUtils;
import com.raitonbl.hermes.smsc.config.messaging.MessageSystemType;
import com.raitonbl.hermes.smsc.config.messaging.MessagingSystem;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.atomic.AtomicBoolean;

@Getter
@Setter
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
        AtomicBoolean isFirst = new AtomicBoolean(true);
        StringBuilder sb = new StringBuilder();
        switch (type) {
            case AWS_SQS -> {
                String name = target.getQueue();
                if (name == null) {
                    name = target.getArn();
                }
                sb.append("aws2-sqs://").append(name);
                ConfigurationUtils.setParameter(sb, isFirst, "useDefaultCredentialsProvider", true);
                if (target.getRegion() != null) {
                    ConfigurationUtils.setParameter(sb, isFirst, "region", target.getRegion());
                }
                ConfigurationUtils.setParameter(sb, isFirst, "operation", "sendBatchMessage");
                ConfigurationUtils.setParameter(sb, isFirst, "amazonSQSClient", "#amazonSQSClient");
            }
            case RABBITMQ -> {
                sb.append("spring-rabbitmq:").append(target.getExchange());
                ConfigurationUtils.setParameter(sb, isFirst, "routingKey", target.getRoutingKey());
                ConfigurationUtils.setParameter(sb, isFirst, "connectionFactory", "#connectionFactory");
            }
            default -> throw new IllegalArgumentException(this.type + " isn't supported");
        }

        return sb.toString();
    }
}
