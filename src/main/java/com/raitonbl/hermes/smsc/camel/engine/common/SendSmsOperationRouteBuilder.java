package com.raitonbl.hermes.smsc.camel.engine.common;

import com.raitonbl.hermes.smsc.camel.common.HermesConstants;
import com.raitonbl.hermes.smsc.camel.common.HermesSystemConstants;
import com.raitonbl.hermes.smsc.camel.engine.smpp.CannotSendSmsRequestException;
import com.raitonbl.hermes.smsc.camel.engine.smpp.CannotDetermineTargetSmppConnectionException;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.smpp.SmppConstants;
import org.springframework.stereotype.Component;

@Component
public class SendSmsOperationRouteBuilder extends RouteBuilder {
    private static final String EXECUTE_TROUGH_POLICY_ROUTE = HermesSystemConstants.Operations.SEND_MESSAGE_SYSTEM_ROUTE + "_EXECUTE";
    private static final String DIRECT_TO_EXECUTE_TROUGH_POLICY_ROUTE = "direct:" + EXECUTE_TROUGH_POLICY_ROUTE;
    public static final String RAW_BODY_HEADER = HermesConstants.HEADER_PREFIX + "SendSmsRequest";
    @Override
    public void configure() throws Exception {
        from(DIRECT_TO_EXECUTE_TROUGH_POLICY_ROUTE)
                .routeId(EXECUTE_TROUGH_POLICY_ROUTE)
                .to(HermesSystemConstants.Operations.DIRECT_TO_SMPP_DECIDER_SYSTEM_ROUTE)
                .choice()
                    .when(header(HermesConstants.SMPP_CONNECTION).isNotNull())
                        .doTry()
                            .setHeader(RAW_BODY_HEADER, simple("${body}"))
                            .setHeader(SmppConstants.DEST_ADDR, simple("${body.destination}"))
                            .setHeader(HermesConstants.SEND_SMS_REQUEST_ID, simple("${body.id}"))
                            .log(LoggingLevel.DEBUG, "Attempting to send SendSmsRequest[\"id\":\"${headers."+ HermesConstants.SEND_SMS_REQUEST_ID +"}\"] through SmppConnection[\"names\":\"${headers."+HermesConstants.SMPP_CONNECTION+".name}\"]")
                            .setBody(simple("${body.content}"))
                            .toD(String.format(HermesSystemConstants.SmppConnection.DIRECT_TO_SMPP_CONNECTION_TRANSMITTER_ROUTE_ID_FORMAT,"${headers." + HermesConstants.SMPP_CONNECTION + ".alias.toUpperCase()}"))
                        .doCatch(Exception.class)
                            .log(LoggingLevel.DEBUG, "Skipping SmppConnection[\"name\":\"${headers."+HermesConstants.SMPP_CONNECTION+".name}\"] during Policy[\"name\":\"${headers."+HermesConstants.POLICY+".id}\",\"version\"=\"${headers."+HermesConstants.POLICY+".version}\"] due to an error")
                            .log(LoggingLevel.ERROR, "${exception.stacktrace}")
                            .setBody(simple("${headers." + RAW_BODY_HEADER + "}"))
                            .removeHeaders(SmppConstants.DEST_ADDR + "|" + HermesConstants.SEND_SMS_REQUEST_ID)
                            .to(DIRECT_TO_EXECUTE_TROUGH_POLICY_ROUTE)
                        .endDoTry()
                        .endChoice()
                    .otherwise()
                        .log(LoggingLevel.DEBUG, "No more policies that apply to SendSmsRequest[\"id\":\"${body.id}\"]")
                        .choice()
                            .when(simple("${exception}").isNotNull())
                                .throwException(CannotSendSmsRequestException.class, "${headers." +HermesConstants.SMPP_CONNECTION+ ".name}")
                            .otherwise()
                                .throwException(CannotDetermineTargetSmppConnectionException.class, "SendSmsRequest[\"id\":\"${body.id}\"]")
                        .endChoice()
                    .endChoice()
                .end();

        from(HermesSystemConstants.Operations.DIRECT_TO_SEND_MESSAGE_SYSTEM_ROUTE)
                .routeId(HermesSystemConstants.Operations.SEND_MESSAGE_SYSTEM_ROUTE)
                .doTry()
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
