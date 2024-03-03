package com.raitonbl.hermes.smsc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raitonbl.hermes.smsc.camel.asyncapi.SendSmsRequest;
import com.raitonbl.hermes.smsc.sdk.CamelConstants;
import com.raitonbl.hermes.smsc.camel.engine.SendSmsRouteBuilder;
import com.raitonbl.hermes.smsc.camel.engine.SmppConnectionRouteBuilder;
import com.raitonbl.hermes.smsc.config.HermesConfiguration;
import com.raitonbl.hermes.smsc.config.rule.CannotDetermineTargetSmppConnectionException;
import com.raitonbl.hermes.smsc.config.rule.Rule;
import com.raitonbl.hermes.smsc.config.rule.RuleSpec;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.smpp.SmppConstants;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

@SpringBootTest
@CamelSpringBootTest
@Import({TestBeanFactory.class})
class SendSmsRequestRouteTests {
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    HermesConfiguration configuration;
    @Autowired
    ProducerTemplate template;
    @Autowired
    CamelContext context;
    @BeforeEach
    void init() {
        TestBeanFactory.setRules(null);
    }
    @Test
    void sendSmsRequest_when_no_rules_and_throw_exception() {
        var sendSmsRequest = SendSmsRequest.builder().id(UUID.randomUUID().toString())
                .from("+25884XXX0000").content("Hi").tags(null).build();
        Assertions.assertThrows(CannotDetermineTargetSmppConnectionException.class,
                () -> {
                    try {
                        template.sendBody(SendSmsRouteBuilder.DIRECT_TO_ROUTE_ID, sendSmsRequest);
                    } catch (CamelExecutionException ex) {
                        if (ex.getCause() instanceof CannotDetermineTargetSmppConnectionException) {
                            throw ex.getCause();
                        }
                        throw ex;
                    }
                });
    }
    @Test
    void sendSmsRoute_then_assert_exchange_for_single_rule() throws Exception {
        SendSmsRoute_then_assert_exchange((from, smpp) -> List.of(
                Rule.builder().name("test").description("test")
                        .spec(RuleSpec.builder().from(from).smpp(smpp).build())
                        .build()
        ));
    }
    @Test
    void sendSmsRoute_then_assert_exchange_for_second_rule() throws Exception {
        SendSmsRoute_then_assert_exchange((from, smpp) -> List.of(
                Rule.builder().name("v4").description("v4")
                        .spec(RuleSpec.builder().from(UUID.randomUUID().toString()).smpp("v4").build())
                        .build(),
                Rule.builder().name("test").description("test")
                        .spec(RuleSpec.builder().from(from).smpp(smpp).build())
                        .build()
        ));
    }
    @Test
    void sendSmsRoute_then_assert_exchange_for_third_rule() throws Exception {
        SendSmsRoute_then_assert_exchange((from, smpp) -> List.of(
                Rule.builder().name("v1").description("v1")
                        .spec(RuleSpec.builder().from(UUID.randomUUID().toString()).smpp("v1").build())
                        .build(),
                Rule.builder().name("v2").description("v2")
                        .spec(RuleSpec.builder().from(UUID.randomUUID().toString()).smpp("v2").build())
                        .build(),
                Rule.builder().name("test").description("test")
                        .spec(RuleSpec.builder().from(from).smpp(smpp).build())
                        .build()
        ));
    }
    void SendSmsRoute_then_assert_exchange(BiFunction<String, String, List<Rule>> p) throws Exception {
        String from = UUID.randomUUID().toString();
        SendSmsRequest sendSmsRequest = SendSmsRequest.builder().id(UUID.randomUUID().toString())
                .from(from).content("Hi").tags(null).build();

        String smppId = UUID.randomUUID().toString();
        String routeId = SmppConnectionRouteBuilder.TRANSMITTER_ROUTE_ID_FORMAT.formatted(smppId);

        AtomicReference<Exchange> reference = new AtomicReference<>(null);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:" + routeId.toUpperCase())
                        .routeId(routeId)
                        .process(reference::set)
                        .end();
            }
        });

        Assertions.assertDoesNotThrow(() -> {
            TestBeanFactory.setRules(p.apply(from, smppId));
            template.sendBody(SendSmsRouteBuilder.DIRECT_TO_ROUTE_ID, sendSmsRequest);
        });

        Assertions.assertEquals(sendSmsRequest.getContent(),reference.get().getIn().getBody());
        Assertions.assertEquals(sendSmsRequest.getId(),reference.get().getIn().getHeader(CamelConstants.SEND_REQUEST_ID));
        Assertions.assertEquals(sendSmsRequest.getDestination(),reference.get().getIn().getHeader(SmppConstants.DEST_ADDR));
    }

}

