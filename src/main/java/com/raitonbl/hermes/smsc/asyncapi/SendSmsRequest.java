package com.raitonbl.hermes.smsc.asyncapi;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SendSmsRequest {
    @JsonProperty("id")
    private String id;
    @JsonProperty("from")
    private String from;
    @JsonProperty("tags")
    private String[] tags;
    @JsonProperty("content")
    private String content;
    @JsonProperty("to")
    private String destination;
}
