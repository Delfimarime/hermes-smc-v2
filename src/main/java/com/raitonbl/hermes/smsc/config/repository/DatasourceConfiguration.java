package com.raitonbl.hermes.smsc.config.repository;

import com.raitonbl.hermes.smsc.config.ConfigurationUtils;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.lang.StringUtils;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

@Data
@Builder
public class DatasourceConfiguration {
    private Provider type;
    // Credentials
    private String username;
    private String password;
    private AuthenticationType authenticationType;
    // ETCD Specifics
    private String[] endpoint;
    private String namespace;
    private Integer fromIndex;
    private Long connectionTimeout;
    private Long keepAliveTime;
    private Long keepAliveTimeout;
    private Integer retryDelay;
    private Integer retryMaxDelay;
    private Integer retryMaxDuration;
    private String servicePath;
    private String prefix;

    public String toObserveURI() {
        switch (this.type) {
            case ETCD -> {
                return getEtcdConsumerURI();
            }
            default -> throw new IllegalArgumentException("Unsupported type=" + this.type);
        }
    }

    public String toConsumerURI() {
        switch (this.type) {
            case ETCD -> {
                return getEtcdConsumerURI();
            }
            default -> throw new IllegalArgumentException("Unsupported type=" + this.type);
        }
    }

    public String toProducerURI() {
        switch (this.type) {
            case ETCD -> {
                return getEtcdProducerURI();
            }
            default -> throw new IllegalArgumentException("Unsupported type=" + this.type);
        }
    }

    private String getEtcdConsumerURI() {
        if (this.endpoint == null) {
            throw new IllegalArgumentException("Expected at least one (1) endpoint");
        }
        String prefix = getDefaultPath();
        StringBuilder sb = new StringBuilder();
        sb.append("etcd3://").append(prefix);
        String endpoints = Stream.of(this.endpoint).filter(StringUtils::isNotBlank)
                .map(each -> "endpoints=" + each).reduce((acc, v) -> acc + "&" + v)
                .orElseThrow(() -> new IllegalArgumentException("Expected at least one (1) endpoint"));

        sb.append("?").append(endpoints);
        AtomicBoolean isFirst = new AtomicBoolean(false);
        if (AuthenticationType.BASIC_AUTH.equals(this.authenticationType)) {
            ConfigurationUtils.setParameter(sb, isFirst, "userName",
                    Optional.ofNullable(this.username)
                            .orElseThrow(() -> new IllegalArgumentException("username is required")));
            ConfigurationUtils.setParameter(sb, isFirst, "password",
                    Optional.ofNullable(this.password)
                            .orElseThrow(() -> new IllegalArgumentException("password is required")));
        } else if (!AuthenticationType.NONE.equals(this.authenticationType)) {
            throw new IllegalArgumentException(this.authenticationType + " isn't supported");
        }

        ConfigurationUtils.setParameter(sb, isFirst, "fromIndex", this.fromIndex);
        ConfigurationUtils.setParameter(sb, isFirst, "retryDelay", this.retryDelay);
        ConfigurationUtils.setParameter(sb, isFirst, "servicePath", this.servicePath);
        ConfigurationUtils.setParameter(sb, isFirst, "retryMaxDelay", this.retryMaxDelay);
        ConfigurationUtils.setParameter(sb, isFirst, "namespace", this.namespace, "/");
        ConfigurationUtils.setParameter(sb, isFirst, "keepAliveTime", this.keepAliveTime == null ? null : this.keepAliveTime + " seconds");
        ConfigurationUtils.setParameter(sb, isFirst, "keepAliveTimeout", this.keepAliveTimeout == null ? null : this.keepAliveTimeout + " seconds");
        ConfigurationUtils.setParameter(sb, isFirst, "retryMaxDuration", this.retryMaxDuration == null ? null : this.retryMaxDuration + " seconds");
        ConfigurationUtils.setParameter(sb, isFirst, "connectionTimeout", this.connectionTimeout == null ? null : this.connectionTimeout + " seconds");
        return sb.toString();
    }

    private String getEtcdProducerURI() {
        return this.getEtcdConsumerURI();
    }

    public String getDefaultPath() {
        return Optional.ofNullable(this.prefix)
                .map(value -> value.endsWith("/") ? value.substring(0, value.length() - 1) : value)
                .map(value -> value.startsWith("/") ? value.substring(1) : value)
                .orElse("hermes/smsc");
    }

}
