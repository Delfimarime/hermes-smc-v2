package com.raitonbl.hermes.smsc.camel.engine;

import com.raitonbl.hermes.smsc.camel.model.SmppConnectionDefinition;
import com.raitonbl.hermes.smsc.config.HermesConfiguration;
import com.raitonbl.hermes.smsc.sdk.HermesSystemConstants;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jcache.JCacheConstants;
import org.apache.camel.component.jcache.policy.JCachePolicy;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.nio.file.NoSuchFileException;

@Component
public class SmppRepositoryRouteBuilder extends RouteBuilder {
    private static final String CACHE_KEY = "GetSmppConnections";
    private static final String READ_FROM_DATASOURCE_ROUTE_ID = HermesSystemConstants.INTERNAL_ROUTE_PREFIX + "_REPOSITORY_DATASOURCE";
    private static final String DIRECT_TO_READ_FROM_DATASOURCE = "direct:" + READ_FROM_DATASOURCE_ROUTE_ID;
    private JCachePolicy jCachePolicy;
    private HermesConfiguration configuration;

    @Override
    public void configure() throws Exception {
        from(DIRECT_TO_READ_FROM_DATASOURCE)
                .routeId(READ_FROM_DATASOURCE_ROUTE_ID)
                .setHeader(JCacheConstants.ACTION, simple("GET"))
                .setHeader(JCacheConstants.KEY, simple(CACHE_KEY))
                .policy(jCachePolicy)
                .to("jcache://" + HermesSystemConstants.KV_CACHE_NAME + "?createCacheIfNotExists=true")
                .choice()
                    .when(body().isNotNull())
                        .log(LoggingLevel.DEBUG, "Retrieving SmppConnection from jcache[key=\"${headers." +
                            JCacheConstants.KEY + "\"}]")
                    .otherwise()
                        .log(LoggingLevel.DEBUG, "Reading SmppConnection from datasource[type=\"filesystem\"]")
                        .doTry()
                            .to("file:"+configuration.getHomeDirectory()+"/config?fileName=smpp.json&noop=true")
                            .unmarshal().json(JsonLibrary.Jackson, SmppConnectionDefinition[].class)
                            .setHeader(JCacheConstants.ACTION, simple("PUT"))
                            .setHeader(JCacheConstants.KEY, simple(CACHE_KEY))
                            .toD("jcache://" + HermesSystemConstants.KV_CACHE_NAME)
                        .doCatch(NoSuchFileException.class)
                            .setBody(constant("[]"))
                        .endDoTry()
                .removeHeader(JCacheConstants.ACTION+"|"+JCacheConstants.KEY);
        setGetAllSmppConnectionRoute();
    }

    private void setGetAllSmppConnectionRoute() {
        from(HermesSystemConstants.DIRECT_TO_GET_ALL_SMPP_CONNECTIONS_ROUTE)
                .routeId(HermesSystemConstants.GET_ALL_SMPP_CONNECTIONS_ROUTE)
                .to(DIRECT_TO_READ_FROM_DATASOURCE);
    }

    @Autowired
    public void setConfiguration(HermesConfiguration configuration) {
        this.configuration = configuration;
    }

    @Autowired
    public void setJCachePolicy(@Qualifier(HermesSystemConstants.KV_CACHE_NAME) JCachePolicy jCachePolicy) {
        this.jCachePolicy = jCachePolicy;
    }

}
