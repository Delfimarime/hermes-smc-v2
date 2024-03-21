package com.raitonbl.hermes.smsc.camel.system.smpp;

import com.raitonbl.hermes.smsc.camel.common.HermesConstants;
import com.raitonbl.hermes.smsc.camel.common.HermesSystemConstants;
import com.raitonbl.hermes.smsc.camel.model.SmppConnectionDefinition;
import com.raitonbl.hermes.smsc.camel.system.common.CircuitBreakerRouteFactory;
import com.raitonbl.hermes.smsc.camel.system.SmppConnectionListenerRouterBuilder;
import com.raitonbl.hermes.smsc.camel.system.datasource.DatasourceEvent;
import com.raitonbl.hermes.smsc.camel.system.datasource.EntityLifecycleListenerRouteFactory;
import com.raitonbl.hermes.smsc.camel.system.datasource.RecordType;
import com.raitonbl.hermes.smsc.camel.system.datasource.DatasourceClient;
import com.raitonbl.hermes.smsc.config.smpp.SmppConfiguration;
import com.raitonbl.hermes.smsc.config.smpp.SmppConnectionType;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.smpp.SmppConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Component
@ConditionalOnBean(value = {DatasourceClient.class, EntityLifecycleListenerRouteFactory.class})
public class SmppLifecycleRouteBuilder extends RouteBuilder {
    private static final Object LOCK = new Object();
    private DatasourceClient client;
    private EntityLifecycleListenerRouteFactory entityLifecycleListenerRouteFactory;

    @Override
    public void configure() throws Exception {
        this.client.<SmppConnectionDefinition>findAll(RecordType.SMPP_CONNECTION)
                .forEach(this::addSmppConnectionRoute);
        entityLifecycleListenerRouteFactory.create(this, RecordType.SMPP_CONNECTION)
                .routeId(HermesSystemConstants.SMPP_CONNECTION_LIFECYCLE_MANAGER)
                .choice()
                    .when(simple("${body.type}").isEqualTo(DatasourceEvent.EventType.SET))
                        .process(this::onSetSmppConnectionEvent)
                    .when(simple("${body.type}").isEqualTo(DatasourceEvent.EventType.DELETE))
                        .process(this::onDeleteSmppConnection)
                    .otherwise()
                        .throwException(UnsupportedOperationException.class, "Cannot process event-type=${body.type}")
                .end();
    }

    private void onSetSmppConnectionEvent(Exchange exchange) {
        synchronized (LOCK) {
            DatasourceEvent event = exchange.getIn().getBody(DatasourceEvent.class);
            if (event == null) {
                return;
            }
            removeSmppConnectionRoute(exchange, (SmppConnectionDefinition) event.getTarget());
            addSmppConnectionRoute((SmppConnectionDefinition) event.getTarget());
        }
    }

    private void onDeleteSmppConnection(Exchange exchange) {
        synchronized (LOCK) {
            DatasourceEvent event = exchange.getIn().getBody(DatasourceEvent.class);
            if (event == null) {
                return;
            }
            removeSmppConnectionRoute(exchange,(SmppConnectionDefinition) event.getTarget());
        }
    }

    private void addSmppConnectionRoute(SmppConnectionDefinition target) {
        switch (target.getSpec().getSmppConnectionType()) {
            case TRANSMITTER, TRANSCEIVER -> {
                addTransmitterSmppConnection(target.getAlias().toUpperCase(), target.getSpec());
            }
            case RECEIVER -> {
                addReceiverSmppConnection(target.getAlias().toUpperCase(), target.getSpec());
            }
            default -> throw new IllegalArgumentException("Smpp{\"name\":\""+target.getName()+"\",\"type\"=\""+target.getSpec().getSmppConnectionType()+"\"} isn't supported");
        }
    }

    private void addTransmitterSmppConnection(String alias, SmppConfiguration configuration) {
        SmppConfiguration targetConfiguration = configuration;
        if (configuration.getSmppConnectionType() == SmppConnectionType.TRANSCEIVER) {
            String redirectTo = String.format(HermesSystemConstants.SMPP_CONNECTION_TRANSCEIVER_CALLBACK_FORMAT, alias);
            from("direct:" + redirectTo)
                    .id(redirectTo)
                    .routeId(redirectTo)
                    .setHeader(HermesConstants.MESSAGE_RECEIVED_BY, simple(alias))
                    .log(LoggingLevel.INFO, "Receiving SendSmsRequest from Smpp{\"name\":\""+alias+"\"}")
                    .to(SmppConnectionListenerRouterBuilder.DIRECT_TO)
                    .end();
            targetConfiguration = configuration.clone();
            targetConfiguration.setRedirectTo(redirectTo);
        }
        final var smppConnectionConfiguration = targetConfiguration;
        String routeId = String.format(HermesSystemConstants.SMPP_CONNECTION_TRANSMITTER_ROUTE_ID_FORMAT, alias).toUpperCase();
        CircuitBreakerRouteFactory.setCircuitBreakerRoute(this, configuration.getCircuitBreakerConfig(), routeId, (id) -> {
            from("direct:" + id)
                    .routeId(id.toUpperCase())
                    .routeDescription("Exposes the capability do send an PDU to %s Short message service center")
                    .log(LoggingLevel.DEBUG, "Sending SendSmsRequest{\"id\":\"${headers." + HermesConstants.SEND_SMS_REQUEST_ID + "}\"} through Smpp{\"alias\":\"" + alias + "\"}")
                    .to(smppConnectionConfiguration.toCamelURI())
                    .end();
        });
    }

    private void addReceiverSmppConnection(String alias, SmppConfiguration configuration) {
        from(configuration.toCamelURI())
                .routeId(String.format(HermesSystemConstants.SMPP_CONNECTION_RECEIVER_ROUTE_ID_FORMAT, alias).toUpperCase())
                .routeDescription(String.format("Listens to an PDU from %s Short message service center", alias))
                .setHeader(HermesConstants.MESSAGE_RECEIVED_BY, simple(alias))
                .setHeader(SmppConstants.PASSWORD, simple(configuration.getPassword()))
                .log(LoggingLevel.INFO, "Receiving SendSmsRequest from Smpp{\"alias\":\""+alias+"\"}")
                .to(SmppConnectionListenerRouterBuilder.DIRECT_TO)
                .end();
    }

    private void removeSmppConnectionRoute(Exchange exchange,SmppConnectionDefinition definition ) {
        Consumer<String> f = (routeId) -> {
            try {
                if (exchange.getContext().getRoute(routeId) == null) {
                    return;
                }
                exchange.getContext().removeRoute(routeId);
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        };
        f.accept(String.format(
                HermesSystemConstants.SMPP_CONNECTION_TRANSMITTER_ROUTE_ID_FORMAT, definition.getAlias()
        ));
        f.accept(String.format(
                HermesSystemConstants.SMPP_CONNECTION_RECEIVER_ROUTE_ID_FORMAT, definition.getAlias()
        ));
        f.accept(String.format(
                HermesSystemConstants.SMPP_CONNECTION_TRANSCEIVER_CALLBACK_FORMAT, definition.getAlias()
        ));
    }

    @Autowired
    public void setClient(DatasourceClient client) {
        this.client = client;
    }

    @Autowired
    public void setRouteFactory(EntityLifecycleListenerRouteFactory entityLifecycleListenerRouteFactory) {
        this.entityLifecycleListenerRouteFactory = entityLifecycleListenerRouteFactory;
    }
}
