package com.raitonbl.hermes.smsc.camel;

import com.raitonbl.hermes.smsc.asyncapi.SendSmsRequest;
import com.raitonbl.hermes.smsc.camel.common.RuleRouteBuilder;
import com.raitonbl.hermes.smsc.common.CamelConstants;
import com.raitonbl.hermes.smsc.config.rule.CannotDetermineSmppTarget;
import com.raitonbl.hermes.smsc.config.rule.Rule;
import com.raitonbl.hermes.smsc.config.rule.TagCriteria;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.commons.validator.routines.RegexValidator;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Stream;

@Component
public class SendSmsThroughSmppRouteBuilder extends RouteBuilder {
    private static final String ROUTE_ID = CamelConstants.SYSTEM_ROUTE_PREFIX + "SEND_MESSAGE";
    public static final String DIRECT_TO_ROUTE_ID = "direct:" + ROUTE_ID;
    private static final String RULES_QUEUE_HEADER = CamelConstants.HEADER_PREFIX + "Rules";
    private static final String TARGET_RULE_HEADER = CamelConstants.HEADER_PREFIX + "TargetRule";
    private static final String TARGET_SMPP_HEADER = CamelConstants.HEADER_PREFIX + "TargetSmpp";
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
                        .toD("direct:{$headers."+TARGET_SMPP_HEADER+"}")
                    .otherwise()
                        .process(this::setTargetRule)
                        .choice()
                            .when(header(TARGET_RULE_HEADER).isNotNull())
                                .to(DIRECT_TO_NEXT_RULE_ROUTE_ID)
                            .otherwise()
                                .log(LoggingLevel.DEBUG, "No more rule(s) that apply to SendSmsRequest[\"id\": \"*\"]")
                                .throwException(CannotDetermineSmppTarget.class,"${body.id}")
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
        if (isCompatible(rule, request)) {
            exchange.getIn().setHeader(TARGET_SMPP_HEADER, String
                    .format(SmppRouteBuilder.TRANSMITTER_ROUTE_ID_FORMAT, rule.getSpec().getSmpp())
                    .toUpperCase()
            );
        }
    }

    private boolean isCompatible(Rule rule, SendSmsRequest request) {
        return isFromCompatible(rule, request) &&
                isDestinationAddrCompatible(rule, request) &&
                areTagsCompatible(rule, request);
    }

    private boolean isFromCompatible(Rule rule, SendSmsRequest request) {
        return rule.getSpec().getFrom() == null || rule.getSpec().getFrom().equals(request.getFrom());
    }

    private boolean isDestinationAddrCompatible(Rule rule, SendSmsRequest request) {
        String destinationAddr = rule.getSpec().getDestinationAddr();
        return destinationAddr == null || new RegexValidator(destinationAddr).isValid(request.getDestination());
    }

    private boolean areTagsCompatible(Rule rule, SendSmsRequest request) {
        TagCriteria[] tagsCriteria = rule.getSpec().getTags();
        if (tagsCriteria == null) {
            return true; // No tag criteria, so always compatible
        }
        for (TagCriteria criteria : tagsCriteria) {
            if (!isTagCriteriaCompatible(criteria, request.getTags())) {
                return false; // Early exit if a mismatch is found
            }
        }

        return true;
    }

    private boolean isTagCriteriaCompatible(TagCriteria criteria, String[] tags) {
        return criteria.getAnyOf() != null ?
                Stream.of(tags).anyMatch(tag -> new RegexValidator(criteria.getAnyOf()).isValid(tag)) :
                Stream.of(criteria.getAllOf())
                        .allMatch(regex -> Stream.of(tags).anyMatch(tag -> new RegexValidator(regex).isValid(tag)));
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
