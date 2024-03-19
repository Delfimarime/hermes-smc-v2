package com.raitonbl.hermes.smsc.camel.system;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raitonbl.hermes.smsc.camel.common.HermesConstants;
import com.raitonbl.hermes.smsc.camel.common.HermesSystemConstants;
import com.raitonbl.hermes.smsc.camel.model.PolicyDefinition;
import com.raitonbl.hermes.smsc.camel.model.SmppConnectionDefinition;
import com.raitonbl.hermes.smsc.config.HermesConfiguration;
import com.raitonbl.hermes.smsc.config.repository.DatasourceConfiguration;
import com.raitonbl.hermes.smsc.config.repository.Provider;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.KeyValue;
import io.etcd.jetcd.kv.GetResponse;
import lombok.Builder;
import lombok.Getter;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.ValueBuilder;
import org.apache.camel.component.etcd3.Etcd3Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class RepositoryRouteBuilder extends RouteBuilder {
    private static final TypeOpts SMPP_CONNECTION_TYPE_OPTS = TypeOpts.builder()
            .prefix("smpp").returnType(SmppConnectionDefinition.class).build();
    private static final TypeOpts POLICIES_TYPE_OPTS = TypeOpts.builder()
            .prefix("policies").returnType(PolicyDefinition.class).build();
    private static final   String DETERMINE_ETCD_KEY_EXPRESSION="${headers." + Etcd3Constants.ETCD_PATH + "}/${headers." + HermesConstants.TARGET + ".prefix}/${headers." + HermesConstants.ENTITY_ID + "}";
    private ObjectMapper  objectMapper;
    private DatasourceConfiguration configuration;
    @Override
    public void configure() throws Exception {
        if (configuration == null || !Provider.ETCD.equals(configuration.getType())) {
            return;
        }
        initAddRoute();
        initFindAllRoute();
        initFindByIdRoute();
        initEditByIdRoute();
        initDeleteByIdRoute();
    }

    private void initAddRoute() {
        from(HermesSystemConstants.DIRECT_TO_REPOSITORY_CREATE)
                .routeId(HermesSystemConstants.REPOSITORY_CREATE)
                .doTry()
                    .choice()
                        .when(header(HermesConstants.OBJECT_TYPE).isEqualTo(HermesConstants.POLICY_OBJECT_TYPE))
                            .setHeader(HermesConstants.TARGET, constant(POLICIES_TYPE_OPTS))
                        .when(header(HermesConstants.OBJECT_TYPE).isEqualTo(HermesConstants.SMPP_CONNECTION_OBJECT_TYPE))
                            .setHeader(HermesConstants.TARGET, constant(SMPP_CONNECTION_TYPE_OPTS))
                        .otherwise()
                            .throwException(IllegalArgumentException.class, "${headers." + HermesConstants.OBJECT_TYPE + "} isn't supported")
                    .end()
                    .choice()
                        .when(header(HermesConstants.ENTITY_ID).isNotNull())
                            .throwException(IllegalArgumentException.class,"${headers."+HermesConstants.ENTITY_ID+"}")
                    .end()
                    .process(exchange->{
                        exchange.getIn().setHeader(HermesConstants.ENTITY_ID, UUID.randomUUID().toString());
                    })
                    .setHeader(Etcd3Constants.ETCD_PATH, constant(configuration.getDefaultPath()))
                    .setHeader(Etcd3Constants.ETCD_PATH, simple(DETERMINE_ETCD_KEY_EXPRESSION))
                    .setHeader(Etcd3Constants.ETCD_ACTION, constant(Etcd3Constants.ETCD_KEYS_ACTION_SET))
                    .process(this::beforePersist)
                    .enrich(configuration.toConsumerURI(), (original, fromEnrich) -> {
                        original.getIn().setBody(original.getIn().getHeader(HermesConstants.TARGET));
                        return original;
                    })
                .endDoTry()
                .doCatch(UnsupportedOperationException.class)
                    .setBody(simple(null))
                .doFinally()
                    .removeHeaders(
                            HermesConstants.TARGET + "|" + Etcd3Constants.ETCD_IS_PREFIX + "|" +
                                    Etcd3Constants.ETCD_ACTION + "|" + Etcd3Constants.ETCD_PATH
                    );
    }

    private void initFindAllRoute() {
            from(HermesSystemConstants.DIRECT_TO_REPOSITORY_FIND_ALL)
                .routeId(HermesSystemConstants.REPOSITORY_FIND_ALL)
                .doTry()
                    .choice()
                    .when( header(HermesConstants.OBJECT_TYPE).isEqualTo(HermesConstants.POLICY_OBJECT_TYPE))
                        .setHeader(HermesConstants.TARGET, constant(POLICIES_TYPE_OPTS))
                    .when( header(HermesConstants.OBJECT_TYPE).isEqualTo(HermesConstants.SMPP_CONNECTION_OBJECT_TYPE))
                        .setHeader(HermesConstants.TARGET, constant(SMPP_CONNECTION_TYPE_OPTS))
                    .otherwise()
                        .throwException(IllegalArgumentException.class, "${headers." + HermesConstants.OBJECT_TYPE + "} isn't supported")
                    .end()
                    .setHeader(Etcd3Constants.ETCD_IS_PREFIX, constant(Boolean.TRUE))
                    .setHeader(Etcd3Constants.ETCD_ACTION, constant(Etcd3Constants.ETCD_KEYS_ACTION_GET))
                    .setHeader(Etcd3Constants.ETCD_PATH, constant(configuration.getDefaultPath()))
                    .setHeader(Etcd3Constants.ETCD_PATH, simple("${headers." + Etcd3Constants.ETCD_PATH + "}/${headers." + HermesConstants.TARGET + ".prefix}"))
                    .enrich(configuration.toConsumerURI(), (original, fromEnrich) -> {
                        GetResponse response = fromEnrich.getIn().getBody(GetResponse.class);
                        String content = response.getKvs().stream().map(KeyValue::getValue)
                                .map(ByteSequence::toString).reduce((acc, v) -> acc + "," + v)
                                .map(v -> "[" + v + "]").orElse("[]");
                        original.getIn().setBody(content);
                        return original;
                    })
                    .process(this::parseCollection)
                .endDoTry()
                .doCatch(UnsupportedOperationException.class)
                    .setBody(simple(null))
                .doFinally()
                    .removeHeaders(
                            HermesConstants.TARGET + "|" + Etcd3Constants.ETCD_IS_PREFIX + "|" +
                                    Etcd3Constants.ETCD_ACTION + "|" + Etcd3Constants.ETCD_PATH
                );
    }

    private void initFindByIdRoute() {
        from(HermesSystemConstants.DIRECT_TO_REPOSITORY_FIND_BY_ID)
                .routeId(HermesSystemConstants.REPOSITORY_FIND_BY_ID)
                .choice()
                    .when(header(HermesConstants.ENTITY_ID).isNull())
                        .setBody(simple(null))
                    .otherwise()
                        .doTry()
                            .choice()
                                .when(header(HermesConstants.OBJECT_TYPE).isEqualTo(HermesConstants.POLICY_OBJECT_TYPE))
                                    .setHeader(HermesConstants.TARGET, constant(POLICIES_TYPE_OPTS))
                                .when(header(HermesConstants.OBJECT_TYPE).isEqualTo(HermesConstants.SMPP_CONNECTION_OBJECT_TYPE))
                                    .setHeader(HermesConstants.TARGET, constant(SMPP_CONNECTION_TYPE_OPTS))
                                .otherwise()
                                    .throwException(IllegalArgumentException.class, "${headers." + HermesConstants.OBJECT_TYPE + "} isn't supported")
                            .end()
                            .setHeader(Etcd3Constants.ETCD_ACTION, constant(Etcd3Constants.ETCD_KEYS_ACTION_GET))
                            .setHeader(Etcd3Constants.ETCD_PATH, constant(configuration.getDefaultPath()))
                            .setHeader(Etcd3Constants.ETCD_PATH, simple("${headers." + Etcd3Constants.ETCD_PATH + "}/${headers." + HermesConstants.TARGET + ".prefix}/${headers." + HermesConstants.ENTITY_ID + "}"))
                            .enrich(configuration.toConsumerURI(), (original, fromEnrich) -> {
                                GetResponse response = fromEnrich.getIn().getBody(GetResponse.class);
                                if(response.getKvs().isEmpty()){
                                    original.getIn().setBody(null);
                                    return original;
                                }
                                KeyValue kv = response.getKvs().getFirst();
                                original.getIn().setBody(kv.getValue().toString());
                                original.getIn().setHeader(HermesConstants.ENTITY_VERSION, kv.getVersion());
                                return original;
                            })
                            .process(this::parseSingleValue)
                        .endDoTry()
                        .doCatch(UnsupportedOperationException.class)
                        .setBody(simple(null))
                        .doFinally()
                        .removeHeaders(
                                HermesConstants.TARGET + "|" + Etcd3Constants.ETCD_IS_PREFIX + "|" +
                                        Etcd3Constants.ETCD_ACTION + "|" + Etcd3Constants.ETCD_PATH
                        )
                .end();
    }

    private void initEditByIdRoute() {
        from(HermesSystemConstants.DIRECT_TO_REPOSITORY_UPDATE_BY_ID)
                .routeId(HermesSystemConstants.REPOSITORY_UPDATE_BY_ID)
                .doTry()
                    .choice()
                        .when(header(HermesConstants.OBJECT_TYPE).isEqualTo(HermesConstants.POLICY_OBJECT_TYPE))
                            .setHeader(HermesConstants.TARGET, constant(POLICIES_TYPE_OPTS))
                        .when(header(HermesConstants.OBJECT_TYPE).isEqualTo(HermesConstants.SMPP_CONNECTION_OBJECT_TYPE))
                            .setHeader(HermesConstants.TARGET, constant(SMPP_CONNECTION_TYPE_OPTS))
                        .otherwise()
                            .throwException(IllegalArgumentException.class, "${headers." + HermesConstants.OBJECT_TYPE + "} isn't supported")
                    .end()
                    .choice()
                        .when(header(HermesConstants.ENTITY_ID).isNull())
                            .throwException(EntityNotFoundException.class,"${headers."+HermesConstants.ENTITY_ID+"}")
                    .end()
                    .setHeader(Etcd3Constants.ETCD_PATH, constant(configuration.getDefaultPath()))
                    .setHeader(Etcd3Constants.ETCD_PATH, simple(DETERMINE_ETCD_KEY_EXPRESSION))
                    .setHeader(Etcd3Constants.ETCD_ACTION, constant(Etcd3Constants.ETCD_KEYS_ACTION_GET))
                    .enrich(configuration.toConsumerURI(), (original, fromEnrich) -> {
                        GetResponse response = fromEnrich.getIn().getBody(GetResponse.class);
                        if (response.getKvs().isEmpty()) {
                            original.setException(new EntityNotFoundException(original.getIn().getHeader(HermesConstants.ENTITY_ID, String.class)));
                        }
                        return original;
                    })
                    .setHeader(Etcd3Constants.ETCD_ACTION, constant(Etcd3Constants.ETCD_KEYS_ACTION_SET))
                    .process(this::beforePersist)
                    .to(configuration.toConsumerURI())
                    .process(exchange -> {
                        exchange.getIn().setBody(exchange.getIn().getHeader(HermesConstants.TARGET));
                    })
                .endDoTry()
                .doCatch(UnsupportedOperationException.class)
                    .setBody(simple(null))
                .doFinally()
                    .removeHeaders(
                            HermesConstants.TARGET + "|" + Etcd3Constants.ETCD_IS_PREFIX + "|" +
                                    Etcd3Constants.ETCD_ACTION + "|" + Etcd3Constants.ETCD_PATH
                    );
    }

    private void initDeleteByIdRoute() {
        from(HermesSystemConstants.DIRECT_TO_REPOSITORY_DELETE_BY_ID)
                .routeId(HermesSystemConstants.REPOSITORY_DELETE_BY_ID)
                .choice()
                    .when(header(HermesConstants.ENTITY_ID).isNull())
                        .setBody(simple(null))
                    .otherwise()
                    .doTry()
                        .choice()
                            .when( header(HermesConstants.OBJECT_TYPE).isEqualTo(HermesConstants.POLICY_OBJECT_TYPE))
                                .setHeader(HermesConstants.TARGET, constant(POLICIES_TYPE_OPTS))
                            .when( header(HermesConstants.OBJECT_TYPE).isEqualTo(HermesConstants.SMPP_CONNECTION_OBJECT_TYPE))
                                .setHeader(HermesConstants.TARGET, constant(SMPP_CONNECTION_TYPE_OPTS))
                            .otherwise()
                                .throwException(IllegalArgumentException.class, "${headers." + HermesConstants.OBJECT_TYPE + "} isn't supported")
                            .end()
                        .setHeader(Etcd3Constants.ETCD_PATH, constant(configuration.getDefaultPath()))
                        .setHeader(Etcd3Constants.ETCD_ACTION, constant(Etcd3Constants.ETCD_KEYS_ACTION_DELETE))
                        .setHeader(Etcd3Constants.ETCD_PATH, simple(DETERMINE_ETCD_KEY_EXPRESSION))
                        .enrich(configuration.toConsumerURI(), (original, fromEnrich) -> {
                            original.getIn().setBody(null);
                            return original;
                        })
                    .endDoTry()
                    .doCatch(UnsupportedOperationException.class)
                        .setBody(simple(null))
                    .doFinally()
                        .removeHeaders(
                                HermesConstants.TARGET + "|" + Etcd3Constants.ETCD_IS_PREFIX + "|" +
                                        Etcd3Constants.ETCD_ACTION + "|" + Etcd3Constants.ETCD_PATH
                        )
                .end();
    }

    private void parseCollection(Exchange exchange) throws Exception{
        var opts = exchange.getIn().getHeader(HermesConstants.TARGET, TypeOpts.class);
        var returnObject = objectMapper.readValue(exchange.getIn().getBody(String.class), opts.returnType.arrayType());
        exchange.getIn().setBody(returnObject);
    }

    private void parseSingleValue(Exchange exchange) throws Exception{
        var opts = exchange.getIn().getHeader(HermesConstants.TARGET, TypeOpts.class);
        var returnObject = objectMapper.readValue(exchange.getIn().getBody(String.class), opts.returnType);
        if (returnObject instanceof  Versioned){
            ((Versioned) returnObject).setVersion(exchange.getIn().getHeader(HermesConstants.ENTITY_VERSION, Long.class));
        }
        exchange.getIn().setBody(returnObject);
    }

    private void beforePersist(Exchange exchange) throws Exception {
        Versioned request = exchange.getIn().getBody(Versioned.class);
        request.setVersion(null);
        request.setId(exchange.getIn().getHeader(HermesConstants.ENTITY_ID, String.class));
        exchange.getIn().setBody(objectMapper.writeValueAsString(request));
        exchange.getIn().setHeader(HermesConstants.TARGET, request);
    }


    @Autowired
    public void setConfiguration(HermesConfiguration configuration) {
        this.configuration = configuration.getDatasource();
    }

    @Autowired
    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Getter
    @Builder
    private static final class TypeOpts {
        private String prefix;
        private Class<?> returnType;
    }

}
