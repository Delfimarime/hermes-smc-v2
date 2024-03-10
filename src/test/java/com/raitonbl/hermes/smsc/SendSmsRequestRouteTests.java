package com.raitonbl.hermes.smsc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raitonbl.hermes.smsc.camel.asyncapi.SendSmsRequest;
import com.raitonbl.hermes.smsc.camel.engine.CircuitBreakerRouteFactory;
import com.raitonbl.hermes.smsc.camel.engine.SendSmsRouteBuilder;
import com.raitonbl.hermes.smsc.camel.engine.SmppConnectionRouteBuilder;
import com.raitonbl.hermes.smsc.config.HermesConfiguration;
import com.raitonbl.hermes.smsc.config.health.CircuitBreakerConfig;
import com.raitonbl.hermes.smsc.config.policy.*;
import com.raitonbl.hermes.smsc.sdk.HermesConstants;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.apache.camel.*;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.smpp.SmppConstants;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.commons.lang.NotImplementedException;
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
import java.util.function.Consumer;

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
    void sendSmsRequest_when_no_rules_and_throw_exception() throws Exception {
        sendSmsRoute_then_assert_cannot_determine_target_smpp(b -> b.id(UUID.randomUUID().toString())
                .from("+25884XXX0000").content("Hi").tags(null), null);
    }

    @Test
    void sendSmsRoute_then_assert_exchange_for_single_rule() throws Exception {
        sendSmsRoute_then_assert_exchange((from, smpp) -> List.of(
                createRule("test", "test", from, smpp, null)
        ));
    }

    @Test
    void sendSmsRoute_then_assert_exchange_for_second_rule() throws Exception {
        sendSmsRoute_then_assert_exchange((from, smpp) -> List.of(
                createRule("v4", "v4", UUID.randomUUID().toString(), "v4", null),
                createRule("test", "test", from, smpp, null)
        ));
    }

    @Test
    void sendSmsRoute_then_assert_exchange_for_third_rule() throws Exception {
        sendSmsRoute_then_assert_exchange((from, smpp) -> List.of(
                createRule("v1", "v1", UUID.randomUUID().toString(), "v1", null),
                createRule("v2", "v2", UUID.randomUUID().toString(), "v2", null),
                createRule("test", "test", from, smpp, null)
        ));
    }

    @Test
    void sendSmsRoute_then_assert_exchange_for_destination_matches_pattern() throws Exception {
        String destination = "25884XXX0001";
        sendSmsRoute_then_assert_exchange(b -> b.id(UUID.randomUUID().toString())
                        .from(UUID.randomUUID().toString()).destination(destination).content("Hi").tags(null),
                (from, smpp) -> List.of(
                        Rule.builder().name("test").description("test")
                                .spec(RuleSpec.builder().destinationAddr("^25884XXX").smpp(smpp).build())
                                .build()
                ));
    }

    @Test
    void sendSmsRoute_then_assert_exchange_for_destination_equal_to() throws Exception {
        String destination = "25884XXX0001";
        sendSmsRoute_then_assert_exchange(b -> b.id(UUID.randomUUID().toString())
                        .from(UUID.randomUUID().toString()).destination(destination).content("Hi").tags(null),
                (from, smpp) -> List.of(
                        Rule.builder().name("test").description("test")
                                .spec(RuleSpec.builder().destinationAddr(destination).smpp(smpp).build())
                                .build()
                ));
    }

    @Test
    void sendSmsRoute_then_assert_exchange_for_any_tag() throws Exception {
        String destination = "25884XXX0001";
        sendSmsRoute_then_assert_exchange(b -> b.id(UUID.randomUUID().toString())
                        .from(UUID.randomUUID().toString()).destination(destination).content("Hi")
                        .tags(new String[]{"X", "Y", "Z", "ANY"}),
                (from, smpp) -> List.of(
                        Rule.builder().name("test").description("test")
                                .spec(
                                        RuleSpec.builder().destinationAddr(destination)
                                                .smpp(smpp).tags(new TagCriteria[]{
                                                                TagCriteria.builder()
                                                                        .anyOf(new String[]{"ANY"}).build()
                                                        }
                                                ).build()
                                )
                                .build()
                ));
    }

    @Test
    void sendSmsRoute_then_assert_exchange_for_any_tag_without_match() throws Exception {
        String destination = "25884XXX0001";
        sendSmsRoute_then_assert_cannot_determine_target_smpp(b -> b.id(UUID.randomUUID().toString())
                .from(UUID.randomUUID().toString()).destination(destination).content("Hi").tags(new String[]{"X", "Y", "Z"}), (from, smpp) -> List.of(
                createRule("test", "test", null, destination, null, TagCriteria.builder().anyOf(new String[]{"ANY"}))
        ));
    }

    @Test
    void sendSmsRoute_then_assert_exchange_for_every_tag() throws Exception {
        String destination = "25884XXX0001";
        sendSmsRoute_then_assert_exchange(b -> b.id(UUID.randomUUID().toString())
                .from(UUID.randomUUID().toString()).destination(destination).content("Hi").tags(new String[]{"X", "Y", "Z", "ANY"}), (from, smpp) -> List.of(
                createRule("test", "test", null, smpp, destination, TagCriteria.builder().allOf(new String[]{"ANY", "Z"}))
        ));
    }

    @Test
    void sendSmsRoute_then_assert_exchange_for_every_tag_without_match() throws Exception {
        String destination = "25884XXX0001";
        sendSmsRoute_then_assert_cannot_determine_target_smpp(b -> b.id(UUID.randomUUID().toString())
                .from(UUID.randomUUID().toString()).destination(destination).content("Hi").tags(new String[]{"X", "Y", "Z"}), (from, smpp) -> List.of(
                createRule("test", "test", null, smpp, destination, TagCriteria.builder().allOf(new String[]{"X", "B"}))
        ));
    }

    @Test
    void sendSmsRoute_then_assert_exchange_in_case_first_smpp_throws_exception() throws Exception {
        String target = "local";
        String routeId = SmppConnectionRouteBuilder.TRANSMITTER_ROUTE_ID_FORMAT.formatted(target);
        sendSmsRoute_then_assert_exchange((from, smpp) -> List.of(
                createRule("test", "test", from, target, null),
                createRule("test", "test", from, smpp, null)
        ), new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:" + routeId.toUpperCase())
                        .routeId(routeId)
                        .throwException(new NotImplementedException("vmz"))
                        .end();
            }
        });
    }

    @Test
    void sendSmsRoute_then_assert_exchange_in_case_first_and_second_smpp_throws_exception() throws Exception {
        String v1Target = "v5";
        String v1RouteId = SmppConnectionRouteBuilder.TRANSMITTER_ROUTE_ID_FORMAT.formatted(v1Target);
        String v2Target = "v57";
        String v2RouteId = SmppConnectionRouteBuilder.TRANSMITTER_ROUTE_ID_FORMAT.formatted(v2Target);
        sendSmsRoute_then_assert_exchange((from, smpp) -> List.of(
                createRule("v1", "v1", from, v1Target, null),
                createRule("v2", "v2", from, v2Target, null),
                createRule("v3", "v3", from, smpp, null)
        ), new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:" + v1RouteId.toUpperCase())
                        .routeId(v1RouteId)
                        .throwException(new NotImplementedException("vmz"))
                        .end();
                from("direct:" + v2RouteId.toUpperCase())
                        .routeId(v2RouteId)
                        .throwException(new NotImplementedException("xp"))
                        .end();
            }
        });
    }

    @Test
    void sendSmsRoute_then_assert_exchange_throw_error_in_case_every_smpp_connection_fails() throws Exception {
        String v1Target = "v65";
        String v1RouteId = SmppConnectionRouteBuilder.TRANSMITTER_ROUTE_ID_FORMAT.formatted(v1Target);
        String v2Target = "b0";
        String v2RouteId = SmppConnectionRouteBuilder.TRANSMITTER_ROUTE_ID_FORMAT.formatted(v2Target);
        String v3Target = "s35";
        String v3RouteId = SmppConnectionRouteBuilder.TRANSMITTER_ROUTE_ID_FORMAT.formatted(v3Target);
        sendSmsRoute_then_assert_throws_exception(b -> b.id(UUID.randomUUID().toString())
                .from("+25884XXX0000").content("Hi").tags(null),(from, smpp) -> List.of(
                createRule("v1", "v1", from, v1Target, null),
                createRule("v2", "v2", from, v2Target, null),
                createRule("v3", "v3", from, v3Target, null)
        ), CannotSendSmsRequestException.class, new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:" + v1RouteId.toUpperCase())
                        .routeId(v1RouteId)
                        .throwException(new NotImplementedException("vmz"))
                        .end();
                from("direct:" + v2RouteId.toUpperCase())
                        .routeId(v2RouteId)
                        .throwException(new NotImplementedException("xp"))
                        .end();
                from("direct:" + v3RouteId.toUpperCase())
                        .routeId(v3RouteId)
                        .throwException(new NotImplementedException("xp"))
                        .end();
            }
        });
    }

    @Test
    void sendSmsRoute_then_assert_exchange_for_destination_doesnt_match_pattern() throws Exception {
        sendSmsRoute_then_assert_cannot_determine_target_smpp(b -> b.id(UUID.randomUUID().toString())
                .from("+25884XXX0000").content("Hi").tags(null), (from, smpp) -> List.of(
                createRule("test", "test", "25884XXX0000", smpp, null)
        ));
    }

    @Test
    void sendSmsRoute_then_assert_exchange_when_first_connection_has_circuit_breaker_open_state() throws Exception {
        String destination = "25884XXX0001";
        String v1Target = UUID.randomUUID().toString();
        String v1RouteId = SmppConnectionRouteBuilder.TRANSMITTER_ROUTE_ID_FORMAT.formatted(v1Target);
        AtomicReference<Integer> counter = new AtomicReference<>(0);
        sendSmsRoute_then_assert_throws_exception(b -> b.id(UUID.randomUUID().toString())
                .from(UUID.randomUUID().toString()).destination(destination).content("Hi").tags(new String[]{"X", "Y", "Z", "ANY"}), (from, smpp) -> List.of(
                createRule("test", "test", null, v1Target, destination, TagCriteria.builder().allOf(new String[]{"ANY", "Z"}))
        ),10, CallNotPermittedException.class, new RouteBuilder() {
            @Override
            public void configure() {
                var cfg = CircuitBreakerConfig.builder()
                        .failureRateThreshold((float) 2)
                        .automaticTransitionFromOpenToHalfOpenEnabled(true)
                        .permittedNumberOfCallsInHalfOpenState(1)
                        .maxWaitDurationInHalfOpenState(1)
                        .minimumNumberOfCalls(2)
                        .build();

                CircuitBreakerRouteFactory.setCircuitBreakerRoute(this, cfg, v1RouteId.toUpperCase(), (routeId) -> {
                    try {
                        from("direct:" + routeId)
                                .routeId(v1RouteId)
                                .process(exchange -> counter.set(counter.get() + 1))
                                .process(exchange -> exchange.getIn().setHeader("testCounter", counter.get()))
                                .log(LoggingLevel.INFO, "cb[vmz]=${headers.testCounter}")
                                .process((ex)->Thread.sleep(200))
                                .choice()
                                .when(simple("${headers.testCounter} < 5"))
                                .throwException(new NotImplementedException("vmz"))
                                .endChoice()
                                .end();
                    }catch (Exception ex){
                        throw new RuntimeException(ex);
                    }
                });
            }
        });
    }


    private Rule createRule(String name, String description, String from, String smpp, String destination, TagCriteria.TagCriteriaBuilder... s) {
        RuleSpec.RuleSpecBuilder specBuilder = RuleSpec.builder().from(from).smpp(smpp).destinationAddr(destination);
        if (s != null) {
            TagCriteria[] criteria = new TagCriteria[s.length];
            for (int i = 0; i < criteria.length; i++) {
                criteria[i] = s[i].build();
            }
            specBuilder.tags(criteria);
        }
        return Rule.builder().name(name).description(description).spec(specBuilder.build()).build();
    }

    void sendSmsRoute_then_assert_exchange(BiFunction<String, String, List<Rule>> getRuleList, RouteBuilder... bs) throws Exception {
        sendSmsRoute_then_assert_exchange(b -> b.id(UUID.randomUUID().toString())
                        .from(UUID.randomUUID().toString()).content("Hi").tags(null),
                getRuleList, bs);
    }

    void sendSmsRoute_then_assert_exchange(Consumer<SendSmsRequest.SendSmsRequestBuilder> c, BiFunction<String, String, List<Rule>> getRules, RouteBuilder... bs) throws Exception {
        sendSmsRoute_then_assert_exchange(c, getRules, 1, bs);
    }

    void sendSmsRoute_then_assert_exchange(Consumer<SendSmsRequest.SendSmsRequestBuilder> c, BiFunction<String, String, List<Rule>> getRules, int retries, RouteBuilder... bs) throws Exception {
        String smppId = UUID.randomUUID().toString();
        String routeId = SmppConnectionRouteBuilder.TRANSMITTER_ROUTE_ID_FORMAT.formatted(smppId);

        AtomicReference<Integer> maxRetries = new AtomicReference<>(retries);
        AtomicReference<Exchange> reference = new AtomicReference<>(null);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:" + routeId.toUpperCase())
                        .routeId(routeId)
                        .process(ex-> System.out.println("-"))
                        .end();
            }
        });
        if (bs != null) {
            for (RouteBuilder b : bs) {
                context.addRoutes(b);
            }
        }
        SendSmsRequest.SendSmsRequestBuilder builder = SendSmsRequest.builder();
        c.accept(builder);
        SendSmsRequest sendSmsRequest = builder.build();

        Assertions.assertDoesNotThrow(() -> {
            int attempts = 1;
            Exception caught;
            do {
                TestBeanFactory.setRules(getRules.apply(sendSmsRequest.getFrom(), smppId));
                reference.set(template.request(SendSmsRouteBuilder.DIRECT_TO_ROUTE_ID, (exchange -> exchange.getIn().setBody(sendSmsRequest))));
                caught = reference.get().getException(Exception.class);
                attempts++;
            } while (attempts <= maxRetries.get());
            if (caught != null) {
                throw caught;
            }
        });
        Assertions.assertNotNull(reference.get());
        Assertions.assertEquals(sendSmsRequest.getContent(), reference.get().getIn().getBody());
        Assertions.assertEquals(sendSmsRequest.getId(), reference.get().getIn().getHeader(HermesConstants.SEND_SMS_REQUEST_ID));
        Assertions.assertEquals(sendSmsRequest.getDestination(), reference.get().getIn().getHeader(SmppConstants.DEST_ADDR));
    }

    void sendSmsRoute_then_assert_cannot_determine_target_smpp(Consumer<SendSmsRequest.SendSmsRequestBuilder> c, BiFunction<String, String, List<Rule>> getRuleList, RouteBuilder... bs) throws Exception {
        sendSmsRoute_then_assert_throws_exception(c, getRuleList, CannotDetermineTargetSmppConnectionException.class, bs);
    }

    <E extends Exception> void sendSmsRoute_then_assert_throws_exception(Consumer<SendSmsRequest.SendSmsRequestBuilder> c, BiFunction<String, String, List<Rule>> getRuleList, Class<E> expectedExceptionType, RouteBuilder... bs) throws Exception {
        sendSmsRoute_then_assert_throws_exception(c, getRuleList, 1, expectedExceptionType, bs);
    }

    <E extends Exception> void sendSmsRoute_then_assert_throws_exception(Consumer<SendSmsRequest.SendSmsRequestBuilder> c, BiFunction<String, String, List<Rule>> getRuleList,int retries, Class<E> expectedExceptionType, RouteBuilder... bs) throws Exception {
        String smppId = UUID.randomUUID().toString();
        SendSmsRequest.SendSmsRequestBuilder builder = SendSmsRequest.builder();
        c.accept(builder);
        SendSmsRequest sendSmsRequest = builder.build();
        if (bs != null) {
            for (RouteBuilder b : bs) {
                context.addRoutes(b);
            }
        }
        AtomicReference<Integer> maxRetries = new AtomicReference<>(retries);
        Assertions.assertThrows(expectedExceptionType,
                () -> {
                    int attempts = 1;
                    Throwable caught;
                    do {
                        if (getRuleList != null) {
                            TestBeanFactory.setRules(getRuleList.apply(sendSmsRequest.getFrom(), smppId));
                        }
                        Exchange outExchange = template
                                .request(SendSmsRouteBuilder.DIRECT_TO_ROUTE_ID,
                                        (exchange -> exchange.getIn().setBody(sendSmsRequest)));
                        caught = outExchange.getException(Exception.class);
                        if (caught == null) {
                            Assertions.fail("expected exception but got None");
                        }
                        if (caught instanceof CamelExecutionException) {
                            caught = caught.getCause();
                        }
                        if (caught.getClass().isAssignableFrom(expectedExceptionType)) {
                           break;
                        }
                        attempts++;
                    } while (attempts <= maxRetries.get());
                    throw caught;
                });
    }

}
