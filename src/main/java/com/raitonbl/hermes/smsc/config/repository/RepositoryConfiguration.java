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
public class RepositoryConfiguration {
    private Provider type;
    // Credentials
    private String username;
    private String password;
    private AuthenticationType authenticationType;

    // FILESYSTEM
    private Integer queueSize;
    private Integer pollThreads;
    private Integer numberOfConcurrentConsumers;

    // ETCD Specifics
    private String prefix;
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

    // COMMON
    private String path;

    public String toObserveURI() {
        switch (this.type) {
            case ETCD -> {
                return getEtcdConsumerURI();
            }
            case FILESYSTEM -> {
                return getFilesystemObserverURI();
            }
            default -> throw new IllegalArgumentException("Unsupported type=" + this.type);
        }
    }

    public String toConsumerURI() {
        switch (this.type) {
            case ETCD -> {
                return getEtcdConsumerURI();
            }
            case FILESYSTEM -> {
                return getFilesystemConsumerURI();
            }
            default -> throw new IllegalArgumentException("Unsupported type=" + this.type);
        }
    }

    public String toProducerURI() {
        switch (this.type) {
            case ETCD -> {
                return getEtcdProducerURI();
            }
            case FILESYSTEM -> {
                return getFilesystemProducerURI();
            }
            default -> throw new IllegalArgumentException("Unsupported type=" + this.type);
        }
    }

    private String getFilesystemObserverURI() {
        AtomicBoolean isFirst = new AtomicBoolean(true);
        StringBuilder sb = new StringBuilder();
        sb.append("file-watch://").append(Optional.ofNullable(this.path).orElseGet(() -> {
            return Optional.ofNullable(System.getenv("HERMES_HOME"))
                    .map(value -> StringUtils.endsWith(value, "/") ? value : value + "/")
                    .map(value -> value + "config").orElse("./config");
        }));
        ConfigurationUtils.setParameter(sb, isFirst, "recursive", Boolean.TRUE);
        ConfigurationUtils.setParameter(sb, isFirst, "queueSize", this.queueSize);
        ConfigurationUtils.setParameter(sb, isFirst, "autoCreate", Boolean.FALSE);
        ConfigurationUtils.setParameter(sb, isFirst, "pollThreads", this.pollThreads);
        ConfigurationUtils.setParameter(sb, isFirst, "concurrentConsumers", this.numberOfConcurrentConsumers);
        return sb.toString();
    }

    private String getFilesystemConsumerURI() {
        AtomicBoolean isFirst = new AtomicBoolean(true);
        StringBuilder sb = new StringBuilder();
        sb.append("fileName://").append(Optional.ofNullable(this.path).orElseGet(() -> {
            return Optional.ofNullable(System.getenv("HERMES_HOME"))
                    .map(value -> StringUtils.endsWith(value, "/") ? value : value + "/")
                    .map(value -> value + "config").orElse("./config");
        }));
        ConfigurationUtils.setParameter(sb, isFirst, "readLock", Boolean.TRUE);
        ConfigurationUtils.setParameter(sb, isFirst, "recursive", Boolean.TRUE);
        ConfigurationUtils.setParameter(sb, isFirst, "directoryMustExist", Boolean.TRUE);
        return sb.toString();
    }

    private String getFilesystemProducerURI() {
        AtomicBoolean isFirst = new AtomicBoolean(false);
        StringBuilder sb = new StringBuilder(getFilesystemConsumerURI());
        ConfigurationUtils.setParameter(sb, isFirst, "forceWrites", Boolean.TRUE);
        ConfigurationUtils.setParameter(sb, isFirst, "fileExist", "Override");
        ConfigurationUtils.setParameter(sb, isFirst, "renameUsingCopy", Boolean.TRUE);
        return sb.toString();
    }

    private String getEtcdConsumerURI() {
        if (this.endpoint == null) {
            throw new IllegalArgumentException("Expected at least one (1) endpoint");
        }
        AtomicBoolean isFirst = new AtomicBoolean(true);
        StringBuilder sb = new StringBuilder();
        sb.append("etcd3://").append(this.path);
        String endpoints = Stream.of(this.endpoint).map(each -> "endpoints=" + each).reduce((acc, v) -> acc + "&" + v)
                .filter(String::isEmpty).orElseThrow(() -> new IllegalArgumentException("Expected at least one (1) endpoint"));

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
        ConfigurationUtils.setParameter(sb, isFirst, "endpoints", endpoints);
        ConfigurationUtils.setParameter(sb, isFirst, "fromIndex", this.fromIndex);
        ConfigurationUtils.setParameter(sb, isFirst, "namespace", this.namespace, "/");
        ConfigurationUtils.setParameter(sb, isFirst, "prefix", this.prefix, "hermes_smsc");
        ConfigurationUtils.setParameter(sb, isFirst, "keepAliveTime", this.keepAliveTime + " seconds");
        ConfigurationUtils.setParameter(sb, isFirst, "keepAliveTimeout", this.keepAliveTimeout + " seconds");
        ConfigurationUtils.setParameter(sb, isFirst, "connectionTimeout", this.connectionTimeout + " seconds");
        ConfigurationUtils.setParameter(sb, isFirst, "retryDelay", this.retryDelay);
        ConfigurationUtils.setParameter(sb, isFirst, "servicePath", this.servicePath);
        ConfigurationUtils.setParameter(sb, isFirst, "retryMaxDelay", this.retryMaxDelay);
        ConfigurationUtils.setParameter(sb, isFirst, "retryMaxDuration", this.retryMaxDuration + " seconds");
        return sb.toString();
    }

    private String getEtcdProducerURI() {
        return this.getEtcdConsumerURI();
    }

}
