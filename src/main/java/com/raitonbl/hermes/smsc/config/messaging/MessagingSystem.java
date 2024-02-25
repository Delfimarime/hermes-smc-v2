package com.raitonbl.hermes.smsc.config.messaging;

import com.raitonbl.hermes.smsc.config.common.ConfigurationUtils;
import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Getter
@Setter
public class MessagingSystem {

    private String arn;
    private String queue;
    private String region;
    private String exchange;
    private String routingKey;

    public static String camelURIFrom(MessageSystemType systemType, MessagingSystem target) {
        return camelURIFrom(systemType, target, null);
    }

    public static String camelURIFrom(MessageSystemType systemType, MessagingSystem target, BiConsumer<StringBuilder, AtomicBoolean> afterSet) {
        AtomicBoolean isFirst = new AtomicBoolean(true);
        StringBuilder sb = new StringBuilder();
        switch (systemType) {
            case AWS_SQS -> {
                String name = target.getQueue();
                if (name == null) {
                    name = target.getArn();
                }
                sb.append("aws2-sqs://").append(name);
                ConfigurationUtils.setParameter(sb, isFirst, "useDefaultCredentialsProvider", true);
                ConfigurationUtils.setParameter(sb, isFirst, "region", target.getRegion());
                ConfigurationUtils.setParameter(sb, isFirst, "operation", "sendBatchMessage");
                ConfigurationUtils.setParameter(sb, isFirst, "amazonSQSClient", "#amazonSQSClient");
            }
            case RABBITMQ -> {
                sb.append("spring-rabbitmq:").append(target.getExchange());
                ConfigurationUtils.setParameter(sb, isFirst, "routingKey", target.getRoutingKey());
                ConfigurationUtils.setParameter(sb, isFirst, "connectionFactory", "#connectionFactory");
            }
            default -> throw new IllegalArgumentException(systemType + " isn't supported");
        }
        if (afterSet != null) {
            afterSet.accept(sb, isFirst);
        }
        return sb.toString();
    }

}
