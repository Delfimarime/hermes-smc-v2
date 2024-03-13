package com.raitonbl.hermes.smsc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raitonbl.hermes.smsc.camel.asyncapi.SendSmsRequest;
import com.raitonbl.hermes.smsc.camel.model.PolicyDefinition;
import com.raitonbl.hermes.smsc.camel.model.SmppConnectionDefinition;
import com.raitonbl.hermes.smsc.config.HermesConfiguration;
import com.raitonbl.hermes.smsc.config.policy.CannotDetermineTargetSmppConnectionException;
import com.raitonbl.hermes.smsc.config.policy.Rule;
import com.raitonbl.hermes.smsc.config.policy.RuleSpec;
import com.raitonbl.hermes.smsc.config.policy.TagCriteria;
import com.raitonbl.hermes.smsc.sdk.HermesConstants;
import com.raitonbl.hermes.smsc.sdk.HermesSystemConstants;
import lombok.Builder;
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

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
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

    @Autowired
    CamelContext context;

    @BeforeEach
    void init() {
        TestBeanFactory.setPolicy();
        TestBeanFactory.setSmppConnectionDefinition();
    }

    @Test
    void sendSmsRequest_when_no_rules_and_throw_exception() throws Exception {
        sendSmsRequest_then_assert_cannot_determine_target_smpp_connection_exception_is_thrown((builder) ->
                builder.numberOfCalls(1)
                        .withRequest(b -> b.id(UUID.randomUUID().toString())
                                .from("+25884XXX0000").content("Hi").tags(null)));
    }

    @Test
    void sendSmsRoute_then_assert_exchange_for_single_policy() throws Exception {
        sendSmsRequest_then_assert_message_is_sent_when_one_retry((from, smpp) -> new PolicyDefinition[]{
                PolicyDefinition.builder().id("test").version("latest")
                        .spec(PolicyDefinition.Spec.builder()
                                .from(from)
                                .resources(
                                        List.of(
                                                PolicyDefinition.ResourceDefinition.builder()
                                                        .id(smpp).build()
                                        )
                                )
                                .build())
                        .build()
        }, (smpp) -> new SmppConnectionDefinition[]{
                SmppConnectionDefinition.builder().id(smpp).name("vmz").alias("vmz").description(null)
                        .configuration(null).tags(null).build()
        }, (smppId) -> new RouteBuilder[]{
                new RouteBuilder() {
                    @Override
                    public void configure() throws Exception {
                        from(String.
                                format(HermesSystemConstants.DIRECT_TO_SMPP_CONNECTION_TRANSMITTER_ROUTE_ID_FORMAT, "VMZ"))
                                .setHeader("JUNIT_ACKNOWLEDGED", constant(true)).end()
                        ;
                    }
                }
        });
    }

    @Test
    void sendSmsRoute_then_assert_exchange_for_second_policy() throws Exception {
        sendSmsRequest_then_assert_message_is_sent_when_one_retry((from, smpp) -> new PolicyDefinition[]{
                PolicyDefinition.builder().id("test").version("latest")
                        .spec(PolicyDefinition.Spec.builder()
                                .from(from)
                                .resources(
                                        List.of(
                                                PolicyDefinition.ResourceDefinition.builder()
                                                        .id(smpp).build()
                                        )
                                )
                                .build())
                        .build()
        }, (smpp) -> new SmppConnectionDefinition[]{
                SmppConnectionDefinition.builder().id("tmz").name("tmz").alias("tmz").description(null)
                        .configuration(null).tags(null).build(),
                SmppConnectionDefinition.builder().id(smpp).name("vmz").alias("vmz").description(null)
                        .configuration(null).tags(null).build()
        }, (smppId) -> new RouteBuilder[]{
                new RouteBuilder() {
                    @Override
                    public void configure() throws Exception {
                        from(String.
                                format(HermesSystemConstants.DIRECT_TO_SMPP_CONNECTION_TRANSMITTER_ROUTE_ID_FORMAT, "VMZ"))
                                .setHeader("JUNIT_ACKNOWLEDGED", constant(true)).end()
                        ;
                    }
                }
        });
    }

    @Test
    void sendSmsRoute_then_assert_exchange_for_third_policy() throws Exception {
        sendSmsRequest_then_assert_message_is_sent_when_one_retry((from, smpp) -> new PolicyDefinition[]{
                PolicyDefinition.builder().id("test").version("latest")
                        .spec(PolicyDefinition.Spec.builder()
                                .from(from)
                                .resources(
                                        List.of(
                                                PolicyDefinition.ResourceDefinition.builder()
                                                        .id(smpp).build()
                                        )
                                )
                                .build())
                        .build()
        }, (smpp) -> new SmppConnectionDefinition[]{
                SmppConnectionDefinition.builder().id("tmz").name("tmz").alias("tmz").description(null)
                        .configuration(null).tags(null).build(),
                SmppConnectionDefinition.builder().id("xmz").name("xmz").alias("xmz").description(null)
                        .configuration(null).tags(null).build(),
                SmppConnectionDefinition.builder().id(smpp).name("vmz").alias("vmz").description(null)
                        .configuration(null).tags(null).build()
        }, (smppId) -> new RouteBuilder[]{
                new RouteBuilder() {
                    @Override
                    public void configure() throws Exception {
                        from(String.
                                format(HermesSystemConstants.DIRECT_TO_SMPP_CONNECTION_TRANSMITTER_ROUTE_ID_FORMAT, "VMZ"))
                                .setHeader("JUNIT_ACKNOWLEDGED", constant(true)).end()
                        ;
                    }
                }
        });
    }

    @Test
    void sendSmsRoute_then_assert_exchange_for_destination_matches_pattern() throws Exception {
        String regex = "^25884XXX";
        String destination = "25884XXX0001";
        sendSmsRequest_then_assert_message_is_sent_when_one_retry(
                (b) -> b.id(UUID.randomUUID().toString())
                        .from(UUID.randomUUID().toString()).destination(destination).content("Hi").tags(null),
                (from, smpp) -> new PolicyDefinition[]{
                        PolicyDefinition.builder().id("test").version("latest")
                                .spec(PolicyDefinition.Spec.builder()
                                        .destination(regex)
                                        .resources(
                                                List.of(
                                                        PolicyDefinition.ResourceDefinition.builder()
                                                                .id(smpp).build()
                                                )
                                        )
                                        .build())
                                .build()
                }, (smpp) -> new SmppConnectionDefinition[]{
                        SmppConnectionDefinition.builder().id(smpp).name("vmz").alias("vmz").description(null)
                                .configuration(null).tags(null).build()
                }, (smppId) -> new RouteBuilder[]{
                        new RouteBuilder() {
                            @Override
                            public void configure() throws Exception {
                                from(String.
                                        format(HermesSystemConstants.DIRECT_TO_SMPP_CONNECTION_TRANSMITTER_ROUTE_ID_FORMAT, "VMZ"))
                                        .setHeader("JUNIT_ACKNOWLEDGED", constant(true)).end()
                                ;
                            }
                        }
                });
    }

    @Test
    void sendSmsRoute_then_assert_exchange_for_destination_is_equal() throws Exception {
        String destination = "25884XXX0001";
        sendSmsRequest_then_assert_message_is_sent_when_one_retry(
                (b) -> b.id(UUID.randomUUID().toString())
                        .from(UUID.randomUUID().toString()).destination(destination).content("Hi").tags(null),
                (from, smpp) -> new PolicyDefinition[]{
                        PolicyDefinition.builder().id("test").version("latest")
                                .spec(PolicyDefinition.Spec.builder()
                                        .destination(destination)
                                        .resources(
                                                List.of(
                                                        PolicyDefinition.ResourceDefinition.builder()
                                                                .id(smpp).build()
                                                )
                                        )
                                        .build())
                                .build()
                }, (smpp) -> new SmppConnectionDefinition[]{
                        SmppConnectionDefinition.builder().id(smpp).name("vmz").alias("vmz").description(null)
                                .configuration(null).tags(null).build()
                }, (smppId) -> new RouteBuilder[]{
                        new RouteBuilder() {
                            @Override
                            public void configure() throws Exception {
                                from(String.
                                        format(HermesSystemConstants.DIRECT_TO_SMPP_CONNECTION_TRANSMITTER_ROUTE_ID_FORMAT, "VMZ"))
                                        .setHeader("JUNIT_ACKNOWLEDGED", constant(true)).end()
                                ;
                            }
                        }
                });
    }

    @Test
    void sendSmsRoute_then_assert_exchange_where_policy_tags_match_smpp_connection() throws Exception {
        String destination = "25884XXX0001";
        sendSmsRequest_then_assert_message_is_sent_when_one_retry(
                (b) -> b.id(UUID.randomUUID().toString())
                        .from(UUID.randomUUID().toString()).destination(destination).content("Hi").tags(null),
                (from, smpp) -> new PolicyDefinition[]{
                        PolicyDefinition.builder().id("test").version("latest")
                                .spec(PolicyDefinition.Spec.builder()
                                        .destination(destination)
                                        .resources(
                                                List.of(
                                                        PolicyDefinition.ResourceDefinition.builder()
                                                                .tags(Map.of("Tag", "Tag"))
                                                                .build()
                                                )
                                        )
                                        .build())
                                .build()
                }, (smpp) -> new SmppConnectionDefinition[]{
                        SmppConnectionDefinition.builder().id(smpp).name("vmz").alias("vmz").description(null)
                                .configuration(null).tags(Map.of("Tag", "Tag")).build()
                }, (smppId) -> new RouteBuilder[]{
                        new RouteBuilder() {
                            @Override
                            public void configure() {
                                from(String.
                                        format(HermesSystemConstants.DIRECT_TO_SMPP_CONNECTION_TRANSMITTER_ROUTE_ID_FORMAT, "VMZ"))
                                        .setHeader("JUNIT_ACKNOWLEDGED", constant(true)).end()
                                ;
                            }
                        }
                });
    }

    @Test
    void sendSmsRoute_then_assert_exchange_where_sms_request_tags_match_policy() throws Exception {
        Map<String, String> tags = Map.of("Tag", UUID.randomUUID().toString());
        String destination = "25884XXX0001";
        sendSmsRequest_then_assert_message_is_sent_when_one_retry(
                (b) -> b.id(UUID.randomUUID().toString())
                        .from(UUID.randomUUID().toString()).destination(destination).content("Hi")
                        .tags(tags),
                (from, smpp) -> new PolicyDefinition[]{
                        PolicyDefinition.builder().id("test").version("latest")
                                .spec(PolicyDefinition.Spec.builder()
                                        .tags(tags)
                                        .resources(
                                                List.of(
                                                        PolicyDefinition.ResourceDefinition.builder()
                                                                .id(smpp).build()
                                                )
                                        )
                                        .build())
                                .build()
                }, (smpp) -> new SmppConnectionDefinition[]{
                        SmppConnectionDefinition.builder().id(smpp).name("vmz").alias("vmz").description(null)
                                .configuration(null).tags(null).build()
                }, (smppId) -> new RouteBuilder[]{
                        new RouteBuilder() {
                            @Override
                            public void configure() {
                                from(String.
                                        format(HermesSystemConstants.DIRECT_TO_SMPP_CONNECTION_TRANSMITTER_ROUTE_ID_FORMAT, "VMZ"))
                                        .setHeader("JUNIT_ACKNOWLEDGED", constant(true)).end()
                                ;
                            }
                        }
                });
    }

    void sendSmsRequest_then_assert_message_is_sent_when_one_retry(BiFunction<String, String, PolicyDefinition[]> createPolicies,
                                                                   Function<String, SmppConnectionDefinition[]> createSmppDefinitions,
                                                                   Function<String, RouteBuilder[]> createRoute) throws Exception {
        sendSmsRequest_then_assert_message_is_sent_when_one_retry(b -> b.id(UUID.randomUUID().toString())
                        .from(UUID.randomUUID().toString()).content("Hi").tags(null),
                createPolicies, createSmppDefinitions, createRoute);
    }

    void sendSmsRequest_then_assert_message_is_sent_when_one_retry(Consumer<SendSmsRequest.SendSmsRequestBuilder> withRequest,
                                                                   BiFunction<String, String, PolicyDefinition[]> createPolicies,
                                                                   Function<String, SmppConnectionDefinition[]> createSmppDefinitions,
                                                                   Function<String, RouteBuilder[]> createRoute) throws Exception {
        sendSmsRequest_then_assert_message_is_sent_when_one_retry(builder -> {
            builder.createPolicies(createPolicies)
                    .createSmppDefinitions(createSmppDefinitions)
                    .createRoute(createRoute)
                    .withRequest(withRequest);
        });
    }

    void sendSmsRequest_then_assert_message_is_sent_when_one_retry(Consumer<SendSmsRequestRouteTestsConfiguration.SendSmsRequestRouteTestsConfigurationBuilder> withConfig) throws Exception {
        sendSmsRequest_then_assert_message_is_sent((builder) -> {
            withConfig.accept(builder);
            builder.numberOfCalls(1);
            builder.expectedExceptionType(null);
        });
    }

    void sendSmsRequest_then_assert_cannot_determine_target_smpp_connection_exception_is_thrown(Consumer<SendSmsRequestRouteTestsConfiguration.SendSmsRequestRouteTestsConfigurationBuilder> withConfig) throws Exception {
        doSendSmsRequestRoute_and_assert_with((builder) -> {
            withConfig.accept(builder);
            builder.expectedExceptionType(CannotDetermineTargetSmppConnectionException.class);
        });
    }

    void sendSmsRequest_then_assert_message_is_sent(Consumer<SendSmsRequestRouteTestsConfiguration.SendSmsRequestRouteTestsConfigurationBuilder> withConfig) throws Exception {
        doSendSmsRequestRoute_and_assert_with(builder -> {
            withConfig.accept(builder);
            builder.expectedExceptionType(null);
        });
    }

    void doSendSmsRequestRoute_and_assert_with(Consumer<SendSmsRequestRouteTestsConfiguration.SendSmsRequestRouteTestsConfigurationBuilder> withConfig) throws Exception {
        var configBuilder = SendSmsRequestRouteTestsConfiguration.builder();
        withConfig.accept(configBuilder);
        var config = configBuilder.build();
        var smppId = UUID.randomUUID().toString();
        var requestBuilder = SendSmsRequest.builder();
        Optional.ofNullable(config.withRequest).ifPresent(x -> x.accept(requestBuilder));
        var sendSmsRequest = requestBuilder.build();
        if (config.createRoute != null) {
            for (RouteBuilder rb : config.createRoute.apply(smppId)) {
                context.addRoutes(rb);
            }
        }
        final AtomicReference<Exchange> fromRoute = new AtomicReference<>();
        PolicyDefinition[] policies = null;
        SmppConnectionDefinition[] smppConnectionDefinitions = null;
        if (config.createPolicies != null) {
            policies = config.createPolicies.apply(sendSmsRequest.getFrom(), smppId);
        }
        if (config.createSmppDefinitions != null) {
            smppConnectionDefinitions = config.createSmppDefinitions.apply(smppId);
        }
        TestBeanFactory.setPolicy(policies);
        TestBeanFactory.setSmppConnectionDefinition(smppConnectionDefinitions);
        AtomicReference<Integer> maxRetries = new AtomicReference<>(config.numberOfCalls);
        if (config.expectedExceptionType != null) {
            Assertions.assertThrows(config.expectedExceptionType,
                    () -> {
                        int attempts = 1;
                        Throwable caught;
                        do {
                            fromRoute.set(template
                                    .request(HermesSystemConstants.DIRECT_TO_SEND_SMS_REQUEST_ROUTE,
                                            (exchange -> exchange.getIn().setBody(sendSmsRequest))));
                            caught = fromRoute.get().getException(Exception.class);
                            if (caught == null) {
                                Assertions.fail("expected exception but got None");
                            }
                            if (caught instanceof CamelExecutionException) {
                                caught = caught.getCause();
                            }
                            if (caught.getClass().isAssignableFrom(config.expectedExceptionType)) {
                                break;
                            }
                            attempts++;
                        } while (attempts <= maxRetries.get());
                        throw caught;
                    });
            return;
        }
        Assertions.assertDoesNotThrow(() -> {
            int attempts = 1;
            Exception caught;
            do {
                fromRoute.set(template.request(HermesSystemConstants.DIRECT_TO_SEND_SMS_REQUEST_ROUTE, (exchange -> exchange.getIn().setBody(sendSmsRequest))));
                caught = fromRoute.get().getException(Exception.class);
                attempts++;
            } while (attempts <= maxRetries.get());
            if (caught != null) {
                throw caught;
            }
        });
        Assertions.assertNotNull(fromRoute.get());
        Assertions.assertEquals(sendSmsRequest.getContent(), fromRoute.get().getIn().getBody());

    }

    @Builder
    static class SendSmsRequestRouteTestsConfiguration {
        int numberOfCalls;
        Function<String, RouteBuilder[]> createRoute;
        Class<? extends Exception> expectedExceptionType;
        Consumer<SendSmsRequest.SendSmsRequestBuilder> withRequest;
        BiFunction<String, String, PolicyDefinition[]> createPolicies;
        Function<String, SmppConnectionDefinition[]> createSmppDefinitions;
    }
}
