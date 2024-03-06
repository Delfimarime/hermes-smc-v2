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
import org.apache.commons.lang.StringUtils;
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
    public static final String TARGET_SMPP_NAME_HEADER = CamelConstants.HEADER_PREFIX + "TargetSmppName";
    private static final String NEXT_RULE_ROUTE_ID = ROUTE_ID + "_GET_RULE";
    private static final String DIRECT_TO_NEXT_RULE_ROUTE_ID = "direct:" + NEXT_RULE_ROUTE_ID;
    private static final String PROCEED_TO_NEXT_ROUTE_ID = ROUTE_ID + "_PROCEED";
    private static final String DIRECT_TO_PROCEED_TO_NEXT_ROUTE_ID = "direct:" + PROCEED_TO_NEXT_ROUTE_ID;
    public static final String RAW_BODY_HEADER = CamelConstants.HEADER_PREFIX + "SendSmsRequest";

    @Override
    public void configure() throws Exception {
        from(DIRECT_TO_PROCEED_TO_NEXT_ROUTE_ID)
                .routeId(PROCEED_TO_NEXT_ROUTE_ID)
                .process(this::setTargetSmpp)
                .choice()
                    .when(header(TARGET_RULE_HEADER).isNotNull())
                        .to(DIRECT_TO_NEXT_RULE_ROUTE_ID)
                    .otherwise()
                        .removeHeaders(TARGET_RULE_HEADER, RULES_QUEUE_HEADER)
                        .log(LoggingLevel.DEBUG, "No more rule(s) that apply to SendSmsRequest[\"id\":\"${body.id}\"]")
                        .throwException(CannotDetermineTargetSmppConnectionException.class, "SendSmsRequest[\"id\":\"${body.id}\"]")
                    .end()
                .end();

        from(DIRECT_TO_NEXT_RULE_ROUTE_ID)
                .routeId(NEXT_RULE_ROUTE_ID)
                .log(LoggingLevel.DEBUG, "Confronting SendSmsRequest[\"id\": \"*\"] with Rule[\"name\":\"${headers." + TARGET_RULE_HEADER + ".name}\"]")
                .process(this::setTargetSmppRouteId)
                .choice()
                    .when(header(TARGET_SMPP_HEADER).isNotNull())
                        .doTry()
                            .setHeader(RAW_BODY_HEADER,simple("${body}"))
                            .setHeader(SmppConstants.DEST_ADDR, simple("${body.destination}"))
                            .setHeader(CamelConstants.SEND_REQUEST_ID, simple("${body.id}"))
                            .log(LoggingLevel.DEBUG, "Attempting to send SendSmsRequest[\"id\":\"${headers."+CamelConstants.SEND_REQUEST_ID+"}\"] through Smpp[\"name\":\"${headers."+TARGET_SMPP_NAME_HEADER+"}\"]")
                            .setBody(simple("${body.content}"))
                            .toD("direct:${headers." + TARGET_SMPP_HEADER + "}")
                        .doCatch(Exception.class)
                            .log(LoggingLevel.DEBUG, "Skipping Rule[\"name\":\"${headers."+TARGET_RULE_HEADER+".name}\"] that allows traffic to Smpp[\"name\":\"${headers."+TARGET_SMPP_NAME_HEADER+"}\"] " +
                                    "because an error occurred")
                            .log(LoggingLevel.ERROR, "${exception.stacktrace}")
                            .setBody(simple("${headers." + RAW_BODY_HEADER + "}"))
                            .removeHeaders(SmppConstants.DEST_ADDR, CamelConstants.SEND_REQUEST_ID)
                            .to(DIRECT_TO_PROCEED_TO_NEXT_ROUTE_ID)
                        .endDoTry()
                        .endChoice()
                    .otherwise()
                        .to(DIRECT_TO_PROCEED_TO_NEXT_ROUTE_ID)
                    .endChoice()
                .end();

        from(DIRECT_TO_ROUTE_ID)
                .routeId(ROUTE_ID)
                .doTry()
                    .enrich(RuleRouteBuilder.DIRECT_TO_READ_RULES_ROUTE_ID, this::setRulesList)
                    .process(this::setTargetSmpp)
                    .to(DIRECT_TO_NEXT_RULE_ROUTE_ID)
                .doFinally()
                    .removeHeaders(
                            TARGET_RULE_HEADER, TARGET_SMPP_HEADER, TARGET_SMPP_NAME_HEADER,
                            RULES_QUEUE_HEADER, SmppConstants.DEST_ADDR, CamelConstants.SEND_REQUEST_ID
                    )
                .end();
    }

    private boolean canSendSms(Rule rule, SendSmsRequest request) {
        return canSendSmsSinceFromIsEqualTo(rule, request)
                && canSendSmsSinceDestinationMatchesPattern(rule, request)
                && canSendSmsSinceTagDefinitionMatch(rule, request);
    }

    private boolean canSendSmsSinceFromIsEqualTo(Rule rule, SendSmsRequest request) {
        return rule.getSpec().getFrom() == null || StringUtils.equals(rule.getSpec().getFrom(),request.getFrom());
    }

    private boolean canSendSmsSinceDestinationMatchesPattern(Rule rule, SendSmsRequest request) {
        String destinationAddr = rule.getSpec().getDestinationAddr();
        if (destinationAddr == null) {
            return true;
        }
        if (request.getDestination() == null) {
            return false;
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
            if (!canSendSmsSinceTagCriteriaMatch(criteria, request.getTags())) {
                return false;
            }
        }
        return true;
    }

    private boolean canSendSmsSinceTagCriteriaMatch(TagCriteria criteria, String[] tags) {
        BiPredicate<String[], String> f = (seq, v) -> {
            for (String p : seq) {
                if (StringUtils.equals(p, v)) {
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
    private void setTargetSmpp(Exchange exchange) {
        Queue<Rule> queue = (Queue<Rule>) exchange.getIn().getHeader(RULES_QUEUE_HEADER);
        if (queue.isEmpty()) {
            exchange.getIn().setHeader(TARGET_RULE_HEADER, null);
            return;
        }
        exchange.getIn().setHeader(TARGET_RULE_HEADER, queue.poll());
    }

    @SuppressWarnings({"unchecked"})
    private Exchange setRulesList(Exchange fromRequest, Exchange fromRoute) {
        List<Rule> collection = fromRoute.getIn().getBody(List.class);
        fromRequest.getIn().setHeader(RULES_QUEUE_HEADER, new ArrayDeque<>(collection));
        return fromRequest;
    }

    private void setTargetSmppRouteId(Exchange exchange) {
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
            exchange.getIn().setHeader(TARGET_SMPP_NAME_HEADER,rule.getSpec().getSmpp());
        }
    }
}
