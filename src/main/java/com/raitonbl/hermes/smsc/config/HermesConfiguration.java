package com.raitonbl.hermes.smsc.config;


import com.raitonbl.hermes.smsc.config.smpp.SmppConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "spring.boot.hermes")
public class HermesConfiguration {
    private Map<String, SmppConfiguration> services;
}
