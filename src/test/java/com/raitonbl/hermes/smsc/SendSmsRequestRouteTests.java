package com.raitonbl.hermes.smsc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raitonbl.hermes.smsc.asyncapi.SendSmsRequest;
import com.raitonbl.hermes.smsc.camel.SendSmsThroughSmppRouteBuilder;
import com.raitonbl.hermes.smsc.camel.SmppRouteBuilder;
import com.raitonbl.hermes.smsc.config.HermesConfiguration;
import com.raitonbl.hermes.smsc.config.rule.CannotDetermineTargetSmppConnectionException;
import com.raitonbl.hermes.smsc.config.rule.Rule;
import com.raitonbl.hermes.smsc.config.rule.RuleSpec;
import org.apache.camel.*;
import org.apache.camel.component.direct.DirectConsumerNotAvailableException;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;

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

    @Test
    void sendSmsRequest_when_no_rules_and_throw_exception() {
        var sendSmsRequest = SendSmsRequest.builder().id(UUID.randomUUID().toString())
                .from("+25884XXX0000").content("Hi").tags(null).build();
        Assertions.assertThrows(CannotDetermineTargetSmppConnectionException.class,
                () -> {
                    try {
                        template.sendBody(SendSmsThroughSmppRouteBuilder.DIRECT_TO_ROUTE_ID, sendSmsRequest);
                    } catch (CamelExecutionException ex) {
                        if (ex.getCause() instanceof CannotDetermineTargetSmppConnectionException) {
                            throw ex.getCause();
                        }
                        throw ex;
                    }
                });
    }

    @Test
    void sendSmsRequest_when_single_rule_match_is_first() {
        sendSmsRequest_when_match_any((smpp,from)-> List.of(
                 Rule.builder().name("test").description("test")
                         .spec(RuleSpec.builder().from(from).smpp(smpp).build())
                         .build()
         ));
    }

    @Test
    void sendSmsRequest_when_single_rule_match_is_second() {
        sendSmsRequest_when_match_any((smpp,from)-> List.of(
                Rule.builder().name("v1").description("v1")
                        .spec(RuleSpec.builder().from(UUID.randomUUID().toString()).smpp("v1").build())
                        .build(),
                Rule.builder().name("test").description("test")
                        .spec(RuleSpec.builder().from(from).smpp(smpp).build())
                        .build()
        ));
    }

    @Test
    void sendSmsRequest_when_single_rule_match_is_third() {
        sendSmsRequest_when_match_any((smpp,from)-> List.of(
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

    void sendSmsRequest_when_match_any(BiFunction<String, String, List<Rule>> p) {
        String from = "+25884XXX0000";
        String targetSmpp = UUID.randomUUID().toString();
        TestBeanFactory.ruleDefinition = p.apply(targetSmpp, from);
        var sendSmsRequest = SendSmsRequest.builder().id(UUID.randomUUID().toString())
                .from(from).content("Hi").tags(null).build();
        AtomicReference<String> routeId = new AtomicReference<>();
        Assertions.assertThrows(DirectConsumerNotAvailableException.class,
                () -> {
                    try {
                        template.sendBody(SendSmsThroughSmppRouteBuilder.DIRECT_TO_ROUTE_ID, sendSmsRequest);
                    } catch (CamelExecutionException ex) {
                        if (ex.getCause() instanceof DirectConsumerNotAvailableException) {
                            routeId.set((String) ex.getExchange().getIn().getHeader(SendSmsThroughSmppRouteBuilder.TARGET_SMPP_HEADER));
                            throw ex.getCause();
                        }
                        throw ex;
                    }
                });
        Assertions.assertEquals(String.format(SmppRouteBuilder.TRANSMITTER_ROUTE_ID_FORMAT, targetSmpp).toUpperCase(), routeId.get());
    }

}

