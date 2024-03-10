package com.raitonbl.hermes.smsc.camel.engine;

import com.raitonbl.hermes.smsc.config.rule.CannotSendSmsRequestException;
import com.raitonbl.hermes.smsc.sdk.HermesConstants;
import com.raitonbl.hermes.smsc.config.rule.CannotDetermineTargetSmppConnectionException;
import com.raitonbl.hermes.smsc.sdk.HermesSystemConstants;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.smpp.SmppConstants;
import org.springframework.stereotype.Component;

@Component
public class SendSmsRouteBuilder extends RouteBuilder {
    public static final String TARGET_SMPP_HEADER = HermesConstants.HEADER_PREFIX + "TargetSmpp";
    private static final String EXECUTE_TROUGH_POLICY_ROUTE = HermesSystemConstants.SEND_SMS_REQUEST_ROUTE + "_EXECUTE";
    private static final String DIRECT_TO_EXECUTE_TROUGH_POLICY_ROUTE = "direct:" + EXECUTE_TROUGH_POLICY_ROUTE;
    public static final String RAW_BODY_HEADER = HermesConstants.HEADER_PREFIX + "SendSmsRequest";

    @Override
    public void configure() throws Exception {
        from(DIRECT_TO_EXECUTE_TROUGH_POLICY_ROUTE)
                .routeId(EXECUTE_TROUGH_POLICY_ROUTE)
                .to(HermesSystemConstants.DIRECT_TO_SMPP_DECIDER_ROUTE)
                .choice()
                    .when(header(HermesConstants.SMPP_CONNECTION).isNotNull())
                        .doTry()
                            .setHeader(RAW_BODY_HEADER, simple("${body}"))
                            .setHeader(SmppConstants.DEST_ADDR, simple("${body.destination}"))
                            .setHeader(HermesConstants.SEND_SMS_REQUEST_ID, simple("${body.id}"))
                            .log(LoggingLevel.DEBUG, "Attempting to send SendSmsRequest[\"id\":\"${headers."+ HermesConstants.SEND_SMS_REQUEST_ID +"}\"] through SmppConnection[\"names\":\"${headers."+HermesConstants.SMPP_CONNECTION+".name}\"]")
                            .setBody(simple("${body.content}"))
                            .toD(String.format(HermesSystemConstants.DIRECT_TO_SMPP_CONNECTION_TRANSMITTER_ROUTE_ID_FORMAT,"${headers." + TARGET_SMPP_HEADER + "}"))
                        .doCatch(Exception.class)
                            .log(LoggingLevel.DEBUG, "Skipping SmppConnection[\"name\":\"${headers."+HermesConstants.SMPP_CONNECTION+".name}\"] during Policy[\"name\":\"${headers."+HermesConstants.POLICY+".id}\",\"version\"=\"${headers."+HermesConstants.POLICY+".version}\"] due to an error")
                            .log(LoggingLevel.ERROR, "${exception.stacktrace}")
                            .setBody(simple("${headers." + RAW_BODY_HEADER + "}"))
                            .removeHeaders(SmppConstants.DEST_ADDR, HermesConstants.SEND_SMS_REQUEST_ID)
                            .to(HermesSystemConstants.DIRECT_TO_SMPP_DECIDER_ROUTE)
                            .to(DIRECT_TO_EXECUTE_TROUGH_POLICY_ROUTE)
                        .endDoTry()
                        .endChoice()
                    .otherwise()
                        .log(LoggingLevel.DEBUG, "No more rule(s) that apply to SendSmsRequest[\"id\":\"${body.id}\"]")
                        .choice()
                            .when(simple("${exception}").isNotNull())
                                .throwException(CannotSendSmsRequestException.class, "${headers." +HermesConstants.SMPP_CONNECTION+ ".name}")
                            .otherwise()
                                .throwException(CannotDetermineTargetSmppConnectionException.class, "SendSmsRequest[\"id\":\"${body.id}\"]")
                        .endChoice()
                    .endChoice()
                .end();

        from(HermesSystemConstants.DIRECT_TO_SEND_SMS_REQUEST_ROUTE)
                .routeId(HermesSystemConstants.SEND_SMS_REQUEST_ROUTE)
                .doTry()
                    .to(HermesSystemConstants.DIRECT_TO_SMPP_DECIDER_ROUTE)
                    .to(DIRECT_TO_EXECUTE_TROUGH_POLICY_ROUTE)
                .doFinally()
                    .removeHeaders(
                            SmppConstants.DEST_ADDR + "|" + HermesConstants.SEND_SMS_REQUEST_ID +
                                    "|" + HermesConstants.POLICY + "|" + HermesConstants.SMPP_CONNECTION + "|" +
                                    HermesConstants.SMPP_CONNECTION_ITERATOR
                    )
                .end();
    }

}
