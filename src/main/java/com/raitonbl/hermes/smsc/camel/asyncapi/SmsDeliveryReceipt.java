package com.raitonbl.hermes.smsc.camel.asyncapi;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import org.jsmpp.util.DeliveryReceiptState;

import java.time.LocalDateTime;

@Setter
@Getter
public class SmsDeliveryReceipt {
    @JsonProperty("id")
    private String id;
    @JsonProperty("status")
    private DeliveryReceiptState status;
    @JsonProperty("received_at")
    private LocalDateTime receivedAt;
    @JsonProperty("smpp")
    private String smpp;
    @JsonProperty("last_attempt_at")
    private LocalDateTime lastAttemptAt;
    @JsonProperty("has_been_delivered")
    private Boolean delivered;
}
