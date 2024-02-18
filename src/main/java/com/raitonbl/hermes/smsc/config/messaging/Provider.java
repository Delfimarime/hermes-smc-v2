package com.raitonbl.hermes.smsc.config.messaging;

import com.raitonbl.hermes.smsc.config.common.ConfigurationUtils;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.atomic.AtomicBoolean;

@Getter
@Setter
public class Provider {

    private String arn;
    private String queue;
    private String region;

    private String exchange;
    private String routingKey;

    @NotNull
    private MessageSystemType type;

    @Null
    private transient String sqsClient;

    @Null
    private transient String connectionFactory;

    public Provider() {
    }

    public Provider(Provider value) {
        this.arn = value.arn;
        this.type = value.type;
        this.queue = value.queue;
        this.region = value.region;
        this.exchange = value.exchange;
        this.sqsClient = value.sqsClient;
        this.routingKey = value.routingKey;
        this.connectionFactory = value.connectionFactory;
    }

    public String toCamelURI() {
        AtomicBoolean isFirst = new AtomicBoolean(true);
        StringBuilder sb = new StringBuilder();
        switch (type) {
            case AWS_SQS -> {
                String name = this.queue;
                if (name == null) {
                    name = this.arn;
                }
                sb.append("aws2-sqs://").append(name);
                ConfigurationUtils.setParameter(sb, isFirst, "useDefaultCredentialsProvider", true);
                if (this.region != null) {
                    ConfigurationUtils.setParameter(sb, isFirst, "region", this.region);
                }
                ConfigurationUtils.setParameter(sb, isFirst, "operation", "sendBatchMessage");
                ConfigurationUtils.setParameter(sb, isFirst, "amazonSQSClient", sqsClient, null, (value) -> "#" + value);
            }
            case RABBITMQ -> {
                sb.append("spring-rabbitmq:").append(this.exchange);
                ConfigurationUtils.setParameter(sb, isFirst, "routingKey", routingKey);
                ConfigurationUtils.setParameter(sb, isFirst, "connectionFactory", connectionFactory, null, (value) -> "#" + value);
            }
            default -> throw new IllegalArgumentException(this.type + " isn't supported");
        }

        return sb.toString();
    }

}
