package com.raitonbl.hermes.smsc.camel.engine;

import com.raitonbl.hermes.smsc.camel.asyncapi.SendSmsRequest;
import com.raitonbl.hermes.smsc.sdk.CamelConstants;
import com.raitonbl.hermes.smsc.config.rule.CannotDetermineTargetSmppConnectionException;
import com.raitonbl.hermes.smsc.config.rule.Rule;
import com.raitonbl.hermes.smsc.config.rule.TagCriteria;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.smpp.SmppConstants;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Component
public class SendSmsRouteBuilder extends RouteBuilder {
    private static final String ROUTE_ID = CamelConstants.SYSTEM_ROUTE_PREFIX + "SEND_MESSAGE";
    public static final String DIRECT_TO_ROUTE_ID = "direct:" + ROUTE_ID;
    private static final String RULES_QUEUE_HEADER = CamelConstants.HEADER_PREFIX + "Rules";
    private static final String TARGET_RULE_HEADER = CamelConstants.HEADER_PREFIX + "TargetRule";
    public static final String TARGET_SMPP_HEADER = CamelConstants.HEADER_PREFIX + "TargetSmpp";
    private static final String NEXT_RULE_ROUTE_ID = CamelConstants.SYSTEM_ROUTE_PREFIX + "DETERMINE_RULE";
    private static final String DIRECT_TO_NEXT_RULE_ROUTE_ID = "direct:" + NEXT_RULE_ROUTE_ID;

    @Override
    public void configure() throws Exception {
        from(DIRECT_TO_NEXT_RULE_ROUTE_ID)
                .routeId(NEXT_RULE_ROUTE_ID)
                .log(LoggingLevel.DEBUG, "Confronting SendSmsRequest[\"id\": \"*\"] with Rule[\"name\":\"${headers." + TARGET_RULE_HEADER + ".name}\"]")
                .process(this::setSmppHeader)
                .choice()
                .when(header(TARGET_SMPP_HEADER).isNotNull())
                .setHeader(SmppConstants.DEST_ADDR, simple("${body.destination}"))
                .setHeader(CamelConstants.SEND_REQUEST_ID, simple("${body.id}"))
                .setBody(simple("${body.content}"))
                .toD("direct:${headers." + TARGET_SMPP_HEADER + "}")
                .otherwise()
                .process(this::setTargetRule)
                .choice()
                .when(header(TARGET_RULE_HEADER).isNotNull())
                .to(DIRECT_TO_NEXT_RULE_ROUTE_ID)
                .otherwise()
                .log(LoggingLevel.DEBUG, "No more rule(s) that apply to SendSmsRequest[\"id\":\"${body.id}\"]")
                .throwException(CannotDetermineTargetSmppConnectionException.class, "SendSmsRequest[\"id\":\"${body.id}\"]")
                .endChoice()
                .endChoice()
                .end();

        from(DIRECT_TO_ROUTE_ID)
                .routeId(ROUTE_ID)
                .enrich(RuleRouteBuilder.DIRECT_TO_READ_RULES_ROUTE_ID, this::setRouteHeader)
                .process(this::setTargetRule)
                .to(DIRECT_TO_NEXT_RULE_ROUTE_ID)
                .removeHeader(TARGET_RULE_HEADER).removeHeader(TARGET_SMPP_HEADER).removeHeader(RULES_QUEUE_HEADER);
    }

    private void setSmppHeader(Exchange exchange) {
        Rule rule = exchange.getIn().getHeader(TARGET_RULE_HEADER, Rule.class);
        SendSmsRequest request = exchange.getIn().getBody(SendSmsRequest.class);
        if (rule == null || request == null) {
            return;
        }
        if (canSendSms(rule, request)) {
            exchange.getIn().setHeader(TARGET_SMPP_HEADER, String
                    .format(SmppConnectionRouteBuilder.TRANSMITTER_ROUTE_ID_FORMAT, rule.getSpec().getSmpp())
                    .toUpperCase()
            );
        }
    }

    private boolean canSendSms(Rule rule, SendSmsRequest request) {
        return canSendSmsSinceFromIsEqualTo(rule, request) && canSendSmsSinceDestinationMatchesPattern(rule, request)
                && canSendSmsSinceTagDefinitionMatch(rule, request);
    }

    private boolean canSendSmsSinceFromIsEqualTo(Rule rule, SendSmsRequest request) {
        return rule.getSpec().getFrom() == null || rule.getSpec().getFrom().equals(request.getFrom());
    }

    private boolean canSendSmsSinceDestinationMatchesPattern(Rule rule, SendSmsRequest request) {
        String destinationAddr = rule.getSpec().getDestinationAddr();
        if (destinationAddr == null) {
            return true;
        }
        Pattern pattern = Pattern.compile(destinationAddr);
        return pattern.asPredicate().test(request.getDestination());
    }

    private boolean canSendSmsSinceTagDefinitionMatch(Rule rule, SendSmsRequest request) {
        TagCriteria[] tagsCriteria = rule.getSpec().getTags();
        if (tagsCriteria == null) {
            return true;
        }
        for (TagCriteria criteria : tagsCriteria) {
            if (!isTagCriteriaTrue(criteria, request.getTags())) {
                return false;
            }
        }

        return true;
    }

    private boolean isTagCriteriaTrue(TagCriteria criteria, String[] tags) {
        BiPredicate<String[], String> f = (seq, v) -> {
            for (String p : seq) {
                Pattern pattern = Pattern.compile(p);
                if (pattern.asPredicate().test(v)) {
                    return true;
                }

            }
            return false;
        };
        return criteria.getAnyOf() != null ?
                Stream.of(tags).anyMatch(tag -> f.test(criteria.getAnyOf(), tag)) :
                Stream.of(criteria.getAllOf())
                        .allMatch(regex -> Stream.of(tags).anyMatch(tag -> f.test(new String[]{regex}, tag)));
    }

    @SuppressWarnings({"unchecked"})
    private void setTargetRule(Exchange exchange) {
        Queue<Rule> queue = (Queue<Rule>) exchange.getIn().getHeader(RULES_QUEUE_HEADER);
        if (queue.isEmpty()) {
            exchange.getIn().setHeader(TARGET_RULE_HEADER, null);
            return;
        }
        exchange.getIn().setHeader(TARGET_RULE_HEADER, queue.poll());
    }

    @SuppressWarnings({"unchecked"})
    private Exchange setRouteHeader(Exchange fromRequest, Exchange fromRoute) {
        List<Rule> collection = fromRoute.getIn().getBody(List.class);
        fromRequest.getIn().setHeader(RULES_QUEUE_HEADER, new ArrayDeque<>(collection));
        return fromRequest;
    }

}
