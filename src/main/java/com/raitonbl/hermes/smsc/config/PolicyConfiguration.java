package com.raitonbl.hermes.smsc.config;

import com.raitonbl.hermes.smsc.sdk.CamelConfiguration;
import com.raitonbl.hermes.smsc.sdk.ConfigurationUtils;
import com.raitonbl.hermes.smsc.config.policy.DatasourceType;
import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.atomic.AtomicBoolean;

@Getter
@Setter
public class PolicyConfiguration implements CamelConfiguration {
    private String host; // Redis/Dragonfly
    private Integer port; // Redis/Dragonfly
    private String username; // Redis/Dragonfly
    private String password;// Redis/Dragonfly
    private Integer database; // Redis/Dragonfly
    private String key; // Redis/Dragonfly/S3
    private String bucket; // S3
    private String prefix; // S3
    private String region; // S3
    private String filename; // Filesystem
    private DatasourceType type;
    private Long timeToLiveInCache;
    private Boolean exposeApi;

    @Override
    public String toCamelURI() {
        StringBuilder sb = new StringBuilder();
        AtomicBoolean isFirst = new AtomicBoolean(true);
        switch (type) {
            case DRAGONFLY, REDIS:
                sb.append("spring-redis://").append(this.host).append(":").append(this.port);
                if (this.username != null || this.password != null) {
                    ConfigurationUtils.setParameter(sb, isFirst, "connectionFactory",
                            "#" + BeanFactory.RULES_REDIS_CONNECTION_FACTORY);
                }
                break;
            case FILESYSTEM:
                sb.append("file://.?fileName=").append(filename);
                break;
            case S3:
                sb.append("aws2-s3://").append(this.bucket);
                ConfigurationUtils.setParameter(sb, isFirst, "prefix", this.prefix);
                ConfigurationUtils.setParameter(sb, isFirst, "region", this.region);
                ConfigurationUtils.setParameter(sb, isFirst, "deleteAfterRead", false);
                ConfigurationUtils.setParameter(sb, isFirst, "operation", "getObject");
                ConfigurationUtils.setParameter(sb, isFirst, "useDefaultCredentialsProvider", "true");
                ConfigurationUtils.setParameter(sb, isFirst, "amazonS3Client", "#"+BeanFactory.AWS_S3_CLIENT);
                break;
        }
        return sb.toString();
    }

    public String toPersistCamelURI() {
        StringBuilder sb = new StringBuilder();
        AtomicBoolean isFirst = new AtomicBoolean(true);
        switch (type) {
            case DRAGONFLY, REDIS:
                sb.append("spring-redis://").append(this.host).append(":").append(this.port);
                if (this.username != null || this.password != null) {
                    ConfigurationUtils.setParameter(sb, isFirst, "connectionFactory",
                            "#" + BeanFactory.RULES_REDIS_CONNECTION_FACTORY);
                }
                ConfigurationUtils.setParameter(sb,isFirst,"command","SET");
                break;
            case FILESYSTEM:
                sb.append("file://.");
                break;
            case S3:
                sb.append("aws2-s3://").append(this.bucket);
                ConfigurationUtils.setParameter(sb, isFirst, "keyName", this.key);
                ConfigurationUtils.setParameter(sb, isFirst, "prefix", this.prefix);
                ConfigurationUtils.setParameter(sb, isFirst, "region", this.region);
                ConfigurationUtils.setParameter(sb, isFirst, "amazonS3Client", "#amazonS3Client");
                ConfigurationUtils.setParameter(sb, isFirst, "useDefaultCredentialsProvider", "true");
                break;
        }
        return sb.toString();
    }
}
