package com.raitonbl.hermes.smsc.camel.system.smpp;

import com.raitonbl.hermes.smsc.camel.common.HermesConstants;
import com.raitonbl.hermes.smsc.camel.common.HermesSystemConstants;
import com.raitonbl.hermes.smsc.camel.system.TestBeanFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

@SpringBootTest
@CamelSpringBootTest
@Import({TestBeanFactory.class})
@TestPropertySource(properties = {
        "spring.boot.hermes.datasource.path=./dist/data",
        "spring.boot.hermes.datasource.type=filesystem",

})
public class RepositoryOnLocalFilesystemRouteTests {

    @Autowired
    ProducerTemplate template;
    @Autowired
    CamelContext context;

    @Test
    void run() throws Exception {
        var fromRequest = template.request(
                HermesSystemConstants.DIRECT_TO_REPOSITORY_FIND_ALL, (
                        exchange -> {
                            exchange.getIn().setBody(null);
                            exchange.getIn().setHeader(HermesConstants.OBJECT_TYPE, HermesConstants.SMPP_CONNECTION_OBJECT_TYPE);
                        }
                )
        );

        if(fromRequest.getException()!=null){
            throw fromRequest.getException();
        }

        System.out.println(fromRequest.getIn().getBody());
        System.out.println(fromRequest.getIn().getHeaders());

    }

}
