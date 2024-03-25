package com.raitonbl.hermes.smsc.config;


import com.raitonbl.hermes.smsc.config.repository.DatasourceConfiguration;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "spring.boot.hermes")
public class HermesConfiguration {
    private Boolean enableSecurityAudit;
    private SendSmsListenerConfiguration listenTo;
    private @NotNull PublishConfiguration publishTo;
    private @NotNull DatasourceConfiguration datasource;

    public boolean isSecurityAuditEnabled() {
        return Optional.ofNullable(enableSecurityAudit).orElse(false);
    }

}
