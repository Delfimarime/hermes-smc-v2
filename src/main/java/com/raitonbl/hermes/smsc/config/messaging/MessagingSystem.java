package com.raitonbl.hermes.smsc.config.messaging;

import com.raitonbl.hermes.smsc.config.common.ConfigurationUtils;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.atomic.AtomicBoolean;

@Getter
@Setter
public class MessagingSystem {

    private String arn;
    private String queue;
    private String region;

    private String exchange;
    private String routingKey;


    @Null
    private transient String sqsClient;

    @Null
    private transient String connectionFactory;

    public MessagingSystem() {
    }

    public MessagingSystem(MessagingSystem value) {
        this.arn = value.arn;
        this.queue = value.queue;
        this.region = value.region;
        this.exchange = value.exchange;
        this.sqsClient = value.sqsClient;
        this.routingKey = value.routingKey;
        this.connectionFactory = value.connectionFactory;
    }



}
