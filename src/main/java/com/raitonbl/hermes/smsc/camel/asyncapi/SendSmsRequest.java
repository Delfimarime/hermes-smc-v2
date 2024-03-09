package com.raitonbl.hermes.smsc.camel.asyncapi;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@Builder
public class SendSmsRequest {
    @JsonProperty("id")
    private String id;
    @JsonProperty("from")
    private String from;
    @JsonProperty("content")
    private String content;
    @JsonProperty("to")
    private String destination;
    @JsonProperty("tags")
    private Map<String, String> tags;
}
