package com.raitonbl.hermes.smsc.asyncapi;

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
    @JsonProperty("source_address")
    private String sourceAddr;
    @JsonProperty("last_attempt_at")
    private LocalDateTime lastAttemptAt;
}
