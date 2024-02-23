package com.raitonbl.hermes.smsc.config;

import com.raitonbl.hermes.smsc.config.common.CamelConfiguration;
import com.raitonbl.hermes.smsc.config.rule.DatasourceType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RuleConfiguration implements CamelConfiguration {
    private String host; // Redis/Dragonfly
    private String port; // Redis/Dragonfly
    private String username; // Redis/Dragonfly
    private String password;// Redis/Dragonfly
    private String key; // Redis/Dragonfly/S3
    private String bucket; // S3
    private String filename; // Filesystem
    private DatasourceType type;
    private Integer timeToLiveInCache;

    @Override
    public String toCamelURI() {
        return null;
    }

}
