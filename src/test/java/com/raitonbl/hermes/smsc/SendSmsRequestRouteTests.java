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

import java.util.List;
import java.util.Optional;
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
        TestBeanFactory.setPolicy();
        TestBeanFactory.setSmppConnectionDefinition();
    }

    @Test
    void sendSmsRequest_when_no_rules_and_throw_exception() throws Exception {
        sendSmsRequest_then_assert_cannot_determine_target_smpp_connection_exception_is_thrown((builder) ->
                builder.numberOfCalls(1)
                        .withRequest(b -> b.id(UUID.randomUUID().toString())
                                .from("+25884XXX0000").content("Hi").tags(null)), null);
    }

    @Test
    void sendSmsRoute_then_assert_exchange_for_single_rule() throws Exception {
        sendSmsRequest_then_assert_message_is_sent_when_one_retry((from, smpp) -> new PolicyDefinition[]{
                PolicyDefinition.builder().id("test").version("latest")
                        .spec(PolicyDefinition.Spec.builder()
                                .from(from)
                                .resources(
                                        List.of(
                                                PolicyDefinition.ResourceDefinition.builder()
                                                        .id(UUID.randomUUID().toString()).build()
                                        )
                                )
                                .build())
                        .build()
        }, null);
    }

    void sendSmsRequest_then_assert_message_is_sent_when_one_retry(BiFunction<String, String, PolicyDefinition[]> createPolicies, RouteBuilder rb) throws Exception {
        sendSmsRequest_then_assert_message_is_sent_when_one_retry(builder -> builder.createPolicies(createPolicies)
                .withRequest(b -> b.id(UUID.randomUUID().toString())
                        .from(UUID.randomUUID().toString()).content("Hi").tags(null)), rb);
    }

    void sendSmsRequest_then_assert_message_is_sent_when_one_retry(Consumer<SendSmsRequestRouteTestsConfiguration.SendSmsRequestRouteTestsConfigurationBuilder> withConfig, RouteBuilder rb) throws Exception {
        sendSmsRequest_then_assert_message_is_sent((builder) -> {
            withConfig.accept(builder);
            builder.numberOfCalls(1);
            builder.expectedExceptionType(null);
        }, rb);
    }

    void sendSmsRequest_then_assert_cannot_determine_target_smpp_connection_exception_is_thrown(Consumer<SendSmsRequestRouteTestsConfiguration.SendSmsRequestRouteTestsConfigurationBuilder> withConfig, RouteBuilder rb) throws Exception {
        doSendSmsRequestRoute_and_assert_with((builder) -> {
            withConfig.accept(builder);
            builder.expectedExceptionType(CannotDetermineTargetSmppConnectionException.class);
        }, rb);
    }

    void sendSmsRequest_then_assert_message_is_sent(Consumer<SendSmsRequestRouteTestsConfiguration.SendSmsRequestRouteTestsConfigurationBuilder> withConfig, RouteBuilder rb) throws Exception {
        doSendSmsRequestRoute_and_assert_with(builder -> {
            withConfig.accept(builder);
            builder.expectedExceptionType(null);
        }, rb);
    }

    void doSendSmsRequestRoute_and_assert_with(Consumer<SendSmsRequestRouteTestsConfiguration.SendSmsRequestRouteTestsConfigurationBuilder> withConfig, RouteBuilder rb) throws Exception {
        var configBuilder = SendSmsRequestRouteTestsConfiguration.builder();
        withConfig.accept(configBuilder);
        var config = configBuilder.build();
        var smppId = UUID.randomUUID().toString();
        var requestBuilder = SendSmsRequest.builder();
        Optional.ofNullable(config.withRequest).ifPresent(x -> x.accept(requestBuilder));
        var sendSmsRequest = requestBuilder.build();
        if (config.routeId != null) {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:" + config.routeId.toUpperCase())
                            .routeId(config.routeId)
                            .process(ex -> System.out.println("-"))
                            .end();
                }
            });
        }
        if (rb != null) {
            context.addRoutes(rb);
        }
        final AtomicReference<Exchange> fromRoute = new AtomicReference<>();
        PolicyDefinition[] policies = null;
        if (config.createPolicies != null) {
            policies = config.createPolicies.apply(sendSmsRequest.getFrom(), smppId);
        }

        TestBeanFactory.setPolicy(policies);
        TestBeanFactory.setSmppConnectionDefinition(config.smppConnections);

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
        Assertions.assertEquals(sendSmsRequest.getId(), fromRoute.get().getIn().getHeader(HermesConstants.SEND_SMS_REQUEST_ID));
        Assertions.assertEquals(sendSmsRequest.getDestination(), fromRoute.get().getIn().getHeader(SmppConstants.DEST_ADDR));

    }

    @Builder
    static class SendSmsRequestRouteTestsConfiguration {
        String routeId;
        int numberOfCalls;
        SmppConnectionDefinition[] smppConnections;
        Class<? extends Exception> expectedExceptionType;
        Consumer<SendSmsRequest.SendSmsRequestBuilder> withRequest;
        BiFunction<String, String, PolicyDefinition[]> createPolicies;
    }

}
