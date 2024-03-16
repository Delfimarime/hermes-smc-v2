package com.raitonbl.hermes.smsc.camel.system.smpp;

import com.raitonbl.hermes.smsc.camel.common.HermesConstants;
import com.raitonbl.hermes.smsc.camel.common.HermesSystemConstants;
import com.raitonbl.hermes.smsc.config.HermesConfiguration;
import com.raitonbl.hermes.smsc.config.repository.DatasourceConfiguration;
import com.raitonbl.hermes.smsc.config.repository.Provider;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.etcd3.Etcd3Constants;
import org.apache.camel.component.file.FileConstants;
import org.apache.camel.processor.aggregate.GroupedBodyAggregationStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static org.apache.camel.support.builder.PredicateBuilder.not;
import static org.apache.camel.support.builder.PredicateBuilder.or;

@Component
public class RepositoryRouteBuilder extends RouteBuilder {
    private DatasourceConfiguration configuration;

    private static final String POLICIES_SUFFIX="POLICIES";
    private static final String SMPP_CONNECTIONS_SUFFIX="SMPP_CONNECTIONS";

    @Override
    public void configure() throws Exception {
        if (configuration == null) {
            return;
        }
        initReadRoute();
    }

    private void initReadRoute() {
        var route = from(HermesSystemConstants.DIRECT_TO_REPOSITORY_FIND_ALL)
                .routeId(HermesSystemConstants.REPOSITORY_FIND_ALL)
                .choice()
                .when( header(HermesConstants.OBJECT_TYPE).isEqualTo(HermesConstants.POLICY_OBJECT_TYPE))
                    .setHeader(HermesConstants.TARGET,simple("POLICIES"))
                .when( header(HermesConstants.OBJECT_TYPE).isEqualTo(HermesConstants.SMPP_CONNECTION_OBJECT_TYPE))
                    .setHeader(HermesConstants.TARGET,simple("SMPP_CONNECTIONS"))
                .otherwise()
                    .throwException(IllegalArgumentException.class, "${headers." + HermesConstants.OBJECT_TYPE + "} isn't supported")
                .end();

                if(Provider.FILESYSTEM.equals(configuration.getType())){
                     route.process(this::setConsumerHeaders)
                            .pollEnrich(configuration.toConsumerURI(), new GroupedBodyAggregationStrategy());
                }

               route.onException(UnsupportedOperationException.class)
                    .handled(Boolean.TRUE)
                    .setBody(simple(null))
                .end();
    }

    private void setConsumerHeaders(Exchange exchange) {
        switch (configuration.getType()) {
            case ETCD -> {
                exchange.getIn().setHeader(Etcd3Constants.ETCD_IS_PREFIX,Boolean.TRUE);
                exchange.getIn().setHeader(Etcd3Constants.ETCD_ACTION, Etcd3Constants.ETCD_KEYS_ACTION_GET);
                exchange.getIn().setHeader(Etcd3Constants.ETCD_PATH, configuration.getPath() + "/" + getObjectTypePrefix(exchange));
            }
            case FILESYSTEM -> exchange.getIn().setHeader(FileConstants.FILE_NAME, getObjectTypePrefix(exchange));
            default -> throw new UnsupportedOperationException();
        }
    }

    private String getObjectTypePrefix(Exchange exchange) {
        return switch (exchange.getIn().getHeader(HermesConstants.OBJECT_TYPE, String.class)) {
            case HermesConstants.POLICY_OBJECT_TYPE -> "policies/";
            case HermesConstants.SMPP_CONNECTION_OBJECT_TYPE -> "smpp/";
            default -> throw new UnsupportedOperationException();
        };
    }

    @Autowired
    public void setConfiguration(HermesConfiguration configuration) {
        this.configuration = configuration.getDatasource();
    }

}
