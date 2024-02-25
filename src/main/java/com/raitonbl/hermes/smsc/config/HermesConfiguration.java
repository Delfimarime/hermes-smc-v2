package com.raitonbl.hermes.smsc.config;


import com.raitonbl.hermes.smsc.config.smpp.SmppConfiguration;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "spring.boot.hermes")
public class HermesConfiguration {
    @NotNull
    private PublishConfiguration publishTo;
    @NotNull
    private SendSmsListenerConfiguration listenTo;
    @NotNull
    private Map<String, SmppConfiguration> services;
    private RuleConfiguration rulesDatasource;
}
