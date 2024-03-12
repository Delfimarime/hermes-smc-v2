package com.raitonbl.hermes.smsc.camel.engine;

import com.raitonbl.hermes.smsc.camel.model.SmppConnectionDefinition;
import com.raitonbl.hermes.smsc.config.HermesConfiguration;
import com.raitonbl.hermes.smsc.sdk.HermesSystemConstants;
import io.vavr.collection.List;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jcache.JCacheConstants;
import org.apache.camel.component.jcache.policy.JCachePolicy;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.nio.file.NoSuchFileException;
import java.util.Collections;

@Component
public class SmppRepositoryRouteBuilder extends RouteBuilder {
    public static final String CACHE_KEY = "GetSmppConnections";
    private static final String READ_FROM_DATASOURCE_ROUTE_ID = HermesSystemConstants.INTERNAL_ROUTE_PREFIX + "REPOSITORY_DATASOURCE";
    private static final String DIRECT_TO_READ_FROM_DATASOURCE = "direct:" + READ_FROM_DATASOURCE_ROUTE_ID;
    private JCachePolicy jCachePolicy;
    private HermesConfiguration configuration;

    @Override
    public void configure() throws Exception {
        String readFileURI = "file:" + configuration.getHomeDirectory() + "/config?fileName=smpp.json&exchangePattern=InOnly&autoCreate=false&noop=true&readLock=none";
        from(DIRECT_TO_READ_FROM_DATASOURCE)
                .routeId(READ_FROM_DATASOURCE_ROUTE_ID)
                .policy(jCachePolicy)
                .setHeader(JCacheConstants.KEY, constant(CACHE_KEY))
                .setHeader(JCacheConstants.ACTION, constant("GET"))
                .to("jcache://" + HermesSystemConstants.KV_CACHE_NAME )
                .choice()
                    .when(body().isNotNull())
                        .log(LoggingLevel.DEBUG, "Retrieving SmppConnection from jcache[key=\"${headers." +
                            JCacheConstants.KEY + "\"}]")
                    .otherwise()
                        .log(LoggingLevel.DEBUG, "Reading SmppConnection from datasource[type=\"filesystem\"]")
                        .doTry()
                            .pollEnrich(readFileURI,1000)
                            .choice()
                                .when(body().isNull())
                                    .setBody(constant("[]"))
                            .end()
                            .unmarshal().json(JsonLibrary.Jackson, SmppConnectionDefinition[].class)
                            .setHeader(JCacheConstants.KEY, constant(CACHE_KEY))
                            .setHeader(JCacheConstants.ACTION, constant("PUT"))
                            .toD("jcache://" + HermesSystemConstants.KV_CACHE_NAME)
                        .endDoTry()
                        .doCatch(NoSuchFileException.class)
                            .setBody(constant((Object) new SmppConnectionDefinition[0]))
                        .endDoTry()
                .endChoice()
                .process(exchange -> exchange.getIn().setBody(
                        List.of(exchange.getIn().getBody(SmppConnectionDefinition[].class))
                ))
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
