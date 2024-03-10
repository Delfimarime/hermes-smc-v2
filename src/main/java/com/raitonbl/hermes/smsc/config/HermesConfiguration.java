package com.raitonbl.hermes.smsc.config;


import com.raitonbl.hermes.smsc.config.smpp.SmppConfiguration;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.Optional;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "spring.boot.hermes")
public class HermesConfiguration {
    @NotNull
    private PublishConfiguration publishTo;
    private SendSmsListenerConfiguration listenTo;
    @NotEmpty
    private Map<String, SmppConfiguration> services;
    private RuleConfiguration rulesDatasource;
    private String homeDirectory;

    public String getHomeDirectory(){
        return  Optional.ofNullable(homeDirectory)
                .or(()->Optional.ofNullable(System.getenv("HERMES_SMSC_HOME")))
                .orElse("./");
    }

}
