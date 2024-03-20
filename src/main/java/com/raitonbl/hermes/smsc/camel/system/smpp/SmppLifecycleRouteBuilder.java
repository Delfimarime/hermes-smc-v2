package com.raitonbl.hermes.smsc.camel.system.smpp;

import com.raitonbl.hermes.smsc.camel.common.HermesSystemConstants;
import com.raitonbl.hermes.smsc.camel.system.datasource.DatasourceEvent;
import com.raitonbl.hermes.smsc.camel.system.datasource.RecordType;
import com.raitonbl.hermes.smsc.camel.system.datasource.EntityLifecycleListenerRouteFactory;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(value = EntityLifecycleListenerRouteFactory.class)
public class SmppLifecycleRouteBuilder extends RouteBuilder {

    private EntityLifecycleListenerRouteFactory entityLifecycleListenerRouteFactory;

    @Override
    public void configure() throws Exception {
        entityLifecycleListenerRouteFactory.create(this, RecordType.SMPP_CONNECTION)
                .routeId(HermesSystemConstants.SMPP_CONNECTION_LIFECYCLE_MANAGER)
                .choice()
                    .when(simple("${body.type}").isEqualTo(DatasourceEvent.EventType.SET))
                        // REMOVE AND RECREATE
                        .log("PUT")
                    .when(simple("${body.type}").isEqualTo(DatasourceEvent.EventType.SET))
                        // REMOVE
                        .log("DELETE")
                    .otherwise()
                        .throwException(UnsupportedOperationException.class,"Cannot process event-type=${body.type}")
                .end();
    }

    @Autowired
    public void setRouteFactory(EntityLifecycleListenerRouteFactory entityLifecycleListenerRouteFactory) {
        this.entityLifecycleListenerRouteFactory = entityLifecycleListenerRouteFactory;
    }
}
