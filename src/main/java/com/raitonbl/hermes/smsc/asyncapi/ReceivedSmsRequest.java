package com.raitonbl.hermes.smsc.asyncapi;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ReceivedSmsRequest {
    @JsonProperty("id")
    private String id;

    @JsonProperty("content")
    private String content;

    @JsonProperty("received_at")
    private LocalDateTime receivedAt;

    @JsonProperty("smpp")
    private String smpp;

    @JsonProperty("source_address")
    private String sourceAddr;

    @JsonProperty("destination_address")
    private String destAddr;
}
