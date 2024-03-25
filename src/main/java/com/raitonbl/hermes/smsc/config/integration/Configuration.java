package com.raitonbl.hermes.smsc.config.integration;

import com.raitonbl.hermes.smsc.config.BeanFactory;
import com.raitonbl.hermes.smsc.config.common.CamelConfiguration;
import com.raitonbl.hermes.smsc.config.common.ConfigurationUtils;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang.StringUtils;

import java.util.concurrent.atomic.AtomicBoolean;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Configuration implements CamelConfiguration, Cloneable {
    private Type type;
    private String name;
    // Hashicorp
    private @NotNull String host;
    private @NotNull String port;
    private @NotNull String token;
    private @NotNull String scheme;
    private @NotNull String engine;

    private transient String path;

    public Configuration(Configuration cfg) {
        this.type = cfg.type;
        this.name = cfg.name;
        this.host = cfg.host;
        this.port = cfg.port;
        this.token = cfg.token;
        this.scheme = cfg.scheme;
        this.engine = cfg.engine;
    }

    @Override
    public String toCamelURI() {
        AtomicBoolean isFirst = new AtomicBoolean(true);
        StringBuilder sb = new StringBuilder();
        switch (getType()) {
            case HASHICORP_VAULT -> {
                sb.append("hashicorp-vault:").append(engine);
                ConfigurationUtils.setParameter(sb, isFirst, "path", getPath());
                ConfigurationUtils.setParameter(sb, isFirst, "host", getHost());
                ConfigurationUtils.setParameter(sb, isFirst, "port", getPort());
                ConfigurationUtils.setParameter(sb, isFirst, "token", getToken());
                ConfigurationUtils.setParameter(sb, isFirst, "scheme", getScheme());
                ConfigurationUtils.setParameter(sb, isFirst, "vaultTemplate",
                        "#"+ BeanFactory.INTEGRATION_CLIENT + StringUtils.capitalize(name)  );
            }
            default -> {
                throw new IllegalArgumentException("type " + getType().name() + " isn't supported");
            }
        }
        return sb.toString();
    }

    @Override
    protected Configuration clone() {
        return new Configuration(this);
    }
}
