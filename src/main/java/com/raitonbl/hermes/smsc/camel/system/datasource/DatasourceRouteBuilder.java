package com.raitonbl.hermes.smsc.camel.system.datasource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raitonbl.hermes.smsc.camel.common.HermesConstants;
import com.raitonbl.hermes.smsc.camel.common.HermesSystemConstants;
import com.raitonbl.hermes.smsc.camel.model.Entity;
import com.raitonbl.hermes.smsc.camel.system.EntityNotFoundException;
import com.raitonbl.hermes.smsc.config.HermesConfiguration;
import com.raitonbl.hermes.smsc.config.repository.DatasourceConfiguration;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KeyValue;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.options.GetOption;
import io.etcd.jetcd.watch.WatchEvent;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.etcd3.Etcd3Constants;
import org.apache.camel.model.ProcessorDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.stream.Stream;

@Component
@ConditionalOnProperty(name = "spring.boot.hermes.datasource.type", havingValue = "etcd")
public class DatasourceRouteBuilder extends RouteBuilder implements DatasourceClient,EntityLifecycleListenerRouteFactory {
    private static final String DETERMINE_ETCD_BASE_PATH = "${headers." + Etcd3Constants.ETCD_PATH + "}/${headers." + HermesConstants.OBJECT_TYPE + ".prefix}";
    private static final String DETERMINE_ETCD_KEY_EXPRESSION = DETERMINE_ETCD_BASE_PATH+"/${headers." + HermesConstants.ENTITY_ID + "}";
    private Client client;
    private ObjectMapper  objectMapper;
    private DatasourceConfiguration configuration;
    @Override
    public void configure() throws Exception {
        initAddRoute();
        initFindAllRoute();
        initFindByIdRoute();
        initEditByIdRoute();
        initDeleteByIdRoute();
    }

    @Override
    public ProcessorDefinition<?> create(RouteBuilder builder, RecordType dbType) {
        if (dbType == null) {
            throw new IllegalArgumentException("null isn't supported");
        }
        String etcdPath = configuration.getDefaultPath() + "/" + dbType.prefix;
        var consumerConfiguration = configuration.clone();
        consumerConfiguration.setPrefix(etcdPath);
        consumerConfiguration.setEnablePrefixMode(Boolean.TRUE);
        return builder.from(consumerConfiguration.toObserveURI())
                .setHeader(HermesConstants.TARGET, builder.constant(dbType))
                .process(exchange -> {
                    WatchEvent event = exchange.getIn().getBody(WatchEvent.class);
                    if (event.getEventType() == null || WatchEvent.EventType.UNRECOGNIZED.equals(event.getEventType())) {
                       exchange.getIn().setBody(null);
                       return;
                    }
                    Long version = event.getKeyValue().getVersion();
                    String payload = event.getKeyValue().getValue().toString();
                    DatasourceEvent.EventType dbEventType = event.getEventType() == WatchEvent.EventType.PUT ?
                            DatasourceEvent.EventType.SET : DatasourceEvent.EventType.DELETE;
                    DatasourceEvent dbEvent = DatasourceEvent.builder().type(dbEventType)
                            .target(doDeserialize(dbType,payload,version)).build();
                    exchange.getIn().setBody(dbEvent);
                });
    }

    private void initAddRoute() {
        from(HermesSystemConstants.DIRECT_TO_REPOSITORY_CREATE)
                .routeId(HermesSystemConstants.REPOSITORY_CREATE)
                .doTry()
                    .choice()
                        .when(header(HermesConstants.OBJECT_TYPE).isNull())
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
                    .enrich(configuration.toProducerURI(), (original, fromEnrich) -> {
                        original.getIn().setBody(original.getIn().getHeader(HermesConstants.TARGET));
                        return original;
                    })
                .endDoTry()
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
                        .when(header(HermesConstants.OBJECT_TYPE).isNull())
                            .throwException(IllegalArgumentException.class, "${headers." + HermesConstants.OBJECT_TYPE + "} isn't supported")
                    .end()
                    .setHeader(Etcd3Constants.ETCD_IS_PREFIX, constant(Boolean.TRUE))
                    .setHeader(Etcd3Constants.ETCD_ACTION, constant(Etcd3Constants.ETCD_KEYS_ACTION_GET))
                    .setHeader(Etcd3Constants.ETCD_PATH, constant(configuration.getDefaultPath()))
                    .setHeader(Etcd3Constants.ETCD_PATH, simple(DETERMINE_ETCD_BASE_PATH))
                    .enrich(configuration.toProducerURI(), (original, fromEnrich) -> {
                        GetResponse response = fromEnrich.getIn().getBody(GetResponse.class);
                        original.getIn().setBody(extractCollectionFromResponse(response));
                        return original;
                    })
                    .process(this::deserializeValues)
                .endDoTry()
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
                                .when(header(HermesConstants.OBJECT_TYPE).isNull())
                                    .throwException(IllegalArgumentException.class, "${headers." + HermesConstants.OBJECT_TYPE + "} isn't supported")
                            .end()
                            .setHeader(Etcd3Constants.ETCD_ACTION, constant(Etcd3Constants.ETCD_KEYS_ACTION_GET))
                            .setHeader(Etcd3Constants.ETCD_PATH, constant(configuration.getDefaultPath()))
                            .setHeader(Etcd3Constants.ETCD_PATH, simple(DETERMINE_ETCD_KEY_EXPRESSION))
                            .enrich(configuration.toProducerURI(), (original, fromEnrich) -> {
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
                            .process(this::deserializeValue)
                        .endDoTry()
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
                        .when(header(HermesConstants.OBJECT_TYPE).isNull())
                            .throwException(IllegalArgumentException.class, "${headers." + HermesConstants.OBJECT_TYPE + "} isn't supported")
                    .end()
                    .choice()
                        .when(header(HermesConstants.ENTITY_ID).isNull())
                            .throwException(EntityNotFoundException.class, "${headers." + HermesConstants.ENTITY_ID + "}")
                    .end()
                    .setHeader(Etcd3Constants.ETCD_PATH, constant(configuration.getDefaultPath()))
                    .setHeader(Etcd3Constants.ETCD_PATH, simple(DETERMINE_ETCD_KEY_EXPRESSION))
                    .setHeader(Etcd3Constants.ETCD_ACTION, constant(Etcd3Constants.ETCD_KEYS_ACTION_GET))
                    .enrich(configuration.toProducerURI(), (original, fromEnrich) -> {
                        GetResponse response = fromEnrich.getIn().getBody(GetResponse.class);
                        if (response.getKvs().isEmpty()) {
                            original.setException(new EntityNotFoundException(original.getIn().getHeader(HermesConstants.ENTITY_ID, String.class)));
                        }
                        return original;
                    })
                    .setHeader(Etcd3Constants.ETCD_ACTION, constant(Etcd3Constants.ETCD_KEYS_ACTION_SET))
                    .process(this::beforePersist)
                    .to(configuration.toProducerURI())
                    .process(exchange -> {
                        exchange.getIn().setBody(exchange.getIn().getHeader(HermesConstants.TARGET));
                    })
                .endDoTry()
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
                            .when( header(HermesConstants.OBJECT_TYPE).isNull())
                                .throwException(IllegalArgumentException.class, "${headers." + HermesConstants.OBJECT_TYPE + "} isn't supported")
                        .end()
                        .setHeader(Etcd3Constants.ETCD_PATH, constant(configuration.getDefaultPath()))
                        .setHeader(Etcd3Constants.ETCD_ACTION, constant(Etcd3Constants.ETCD_KEYS_ACTION_DELETE))
                        .setHeader(Etcd3Constants.ETCD_PATH, simple(DETERMINE_ETCD_KEY_EXPRESSION))
                        .enrich(configuration.toProducerURI(), (original, fromEnrich) -> {
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

    @Override
    public <E extends Entity> Stream<E> findAll(RecordType objectType) throws Exception {
        String path = configuration.getDefaultPath() + "/" + objectType.prefix;
        GetResponse response = this.client.getKVClient().get(
                ByteSequence.from(path, StandardCharsets.UTF_8),
                GetOption.newBuilder().isPrefix(Boolean.TRUE).build()
        ).get();
        E[] definitions = doDeserializeValues(objectType, extractCollectionFromResponse(response));
        return Stream.of(definitions);
    }

    private String extractCollectionFromResponse(GetResponse response){
        return response.getKvs().stream().map(KeyValue::getValue)
                .map(ByteSequence::toString).reduce((acc, v) -> acc + "," + v)
                .map(v -> "[" + v + "]").orElse("[]");
    }

    private void deserializeValue(Exchange exchange) throws JsonProcessingException {
        var dbType = exchange.getIn().getHeader(HermesConstants.TARGET, RecordType.class);
        var version=exchange.getIn().getHeader(HermesConstants.ENTITY_VERSION, Long.class);
        exchange.getIn().setBody(doDeserialize(dbType,exchange.getIn().getBody(String.class),version));
    }

    private void deserializeValues(Exchange exchange) throws JsonProcessingException {
        RecordType dbType = exchange.getIn().getHeader(HermesConstants.TARGET, RecordType.class);
        exchange.getIn().setBody(doDeserializeValues(dbType,exchange.getIn().getBody(String.class)));
    }

    @SuppressWarnings({"unchecked"})
    private <E> E[] doDeserializeValues(RecordType dbType, String value) throws JsonProcessingException {
        return (E[]) objectMapper.readValue(value, dbType.javaType.arrayType());
    }

    private Entity doDeserialize(RecordType dbType, String value, Long version) throws JsonProcessingException {
        var returnObject = objectMapper.readValue(value, dbType.javaType);
        returnObject.setVersion(version);
        return returnObject;
    }

    private void beforePersist(Exchange exchange) throws Exception {
        Entity request = exchange.getIn().getBody(Entity.class);
        request.setVersion(null);
        request.setId(exchange.getIn().getHeader(HermesConstants.ENTITY_ID, String.class));
        exchange.getIn().setBody(objectMapper.writeValueAsString(request));
        exchange.getIn().setHeader(HermesConstants.TARGET, request);
    }

    @Autowired
    public void setClient(Client client) {
        this.client = client;
    }

    @Autowired
    public void setConfiguration(HermesConfiguration configuration) {
        this.configuration = configuration.getDatasource();
    }

    @Autowired
    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

}
