package com.raitonbl.hermes.smsc.camel.system.datasource;

import com.raitonbl.hermes.smsc.camel.common.HermesConstants;
import com.raitonbl.hermes.smsc.camel.common.HermesSystemConstants;
import com.raitonbl.hermes.smsc.camel.model.SmppConnectionDefinition;
import com.raitonbl.hermes.smsc.camel.system.TestBeanFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@CamelSpringBootTest
@Import({TestBeanFactory.class})
@TestPropertySource(properties = {
        "spring.boot.hermes.datasource.type=etcd",
        "spring.boot.hermes.datasource.prefix=/hermes/smsc",
        "spring.boot.hermes.datasource.endpoint=http://localhost:2379",
        "spring.boot.hermes.datasource.authentication-type=none"
})
public class RepositoryOnLocalFilesystemRouteTests {
    @Autowired
    ProducerTemplate template;
    @Autowired
    CamelContext context;
    @Autowired
    EntityLifecycleListenerRouteFactory factory;

    @Test
    void run() throws Exception {
        var fromRequest = template.request(
                HermesSystemConstants.DIRECT_TO_REPOSITORY_CREATE, (
                        exchange -> {
                            exchange.getIn().setBody(SmppConnectionDefinition.builder().name("mobitel").description("<description/>").build());
                            //exchange.getIn().setHeader(HermesConstants.ENTITY_ID, "45f20381-db70-4673-b327-44efbc9d7991");
                            exchange.getIn().setHeader(HermesConstants.OBJECT_TYPE, DatasourceType.SMPP_CONNECTION);
                        }
                )
        );

        if (fromRequest.getException() != null) {
            throw fromRequest.getException();
        }

        System.out.println(fromRequest.getIn().getBody());
        System.out.println(fromRequest.getIn().getHeaders());

    }

    @Test
    void execute() throws Exception {
        Thread.sleep(2000);
        var fromRequest = template.request(
                HermesSystemConstants.DIRECT_TO_REPOSITORY_CREATE, (
                        exchange -> {
                            exchange.getIn().setBody(SmppConnectionDefinition.builder().name("mobitel").description("<description/>").build());
                            //exchange.getIn().setHeader(HermesConstants.ENTITY_ID, "45f20381-db70-4673-b327-44efbc9d7991");
                            exchange.getIn().setHeader(HermesConstants.OBJECT_TYPE, DatasourceType.SMPP_CONNECTION);
                        }
                )
        );
        Thread.sleep(1000);
        if (fromRequest.getException() != null) {
            throw fromRequest.getException();
        }
        System.out.println(fromRequest.getIn().getBody());
        System.out.println(fromRequest.getIn().getHeaders());

    }

}
