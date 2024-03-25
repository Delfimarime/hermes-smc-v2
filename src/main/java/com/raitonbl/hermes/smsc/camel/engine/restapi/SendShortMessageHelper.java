package com.raitonbl.hermes.smsc.camel.engine.restapi;

import com.raitonbl.hermes.smsc.camel.common.HermesConstants;
import com.raitonbl.hermes.smsc.camel.common.HermesSystemConstants;
import com.raitonbl.hermes.smsc.camel.engine.common.SendSmsRequest;
import com.raitonbl.hermes.smsc.camel.engine.smpp.SmppConnectionNotFoundException;
import com.raitonbl.hermes.smsc.camel.model.SmppConnectionDefinition;
import com.raitonbl.hermes.smsc.config.policy.CannotDetermineTargetSmppConnectionException;
import com.raitonbl.hermes.smsc.config.policy.CannotSendSmsRequestException;
import org.apache.camel.Exchange;
import org.apache.camel.component.direct.DirectConsumerNotAvailableException;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.TryDefinition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import static org.apache.camel.builder.Builder.*;

public final class SendShortMessageHelper {

    private SendShortMessageHelper() {
    }

    public static ProcessorDefinition<?> with(ProcessorDefinition<?> routeDefinition) {
       return routeDefinition
                .process(exchange -> exchange.getIn()
                        .getBody(SendSmsRequest.class)
                        .setFrom(exchange.getIn().getHeader(HermesConstants.AUTHORIZATION, String.class)))
                .choice()
                    .when(header(HermesConstants.ENTITY_ID).isNotNull())
                        .enrich(HermesSystemConstants
                                .CrudOperations.DIRECT_TO_FIND_SMPP_CONNECTION_BY_ID, (exchange, fromComponent) -> {
                            if (fromComponent == null) {
                                exchange.setException(new SmppConnectionNotFoundException(
                                        exchange.getIn().getHeader(HermesConstants.ENTITY_ID, String.class)
                                ));
                            }else {
                                exchange.getIn().getHeader(HermesConstants.SMPP_CONNECTION,
                                        fromComponent.getIn().getBody(SmppConnectionDefinition.class));
                            }
                            return exchange;
                        })
                .end()
                .choice()
                    .when(header(HermesConstants.SMPP_CONNECTION).isNotNull())
                        .toD(String.format(HermesSystemConstants.SmppConnection.
                                DIRECT_TO_SMPP_CONNECTION_TRANSMITTER_ROUTE_ID_FORMAT,
                                "${headers." + HermesConstants.SMPP_CONNECTION + ".alias.toUpperCase()}"))
                    .otherwise()
                        .to(HermesSystemConstants.Operations.DIRECT_TO_SEND_MESSAGE_SYSTEM_ROUTE)
                .end()
                .setBody(simple(null))
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.NO_CONTENT.value()));
    }

    public static TryDefinition andCatch(String operationId, TryDefinition catchDefinition) {
        return catchDefinition
                .doCatch(DirectConsumerNotAvailableException.class)
                    .log("${exception.stacktrace}")
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.FORBIDDEN.value()))
                    .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                    .setBody(ZalandoProblemDefinition.forbidden(operationId, (builder -> builder
                            .detail("The specified Smpp Connection isn't available")
                            .type("/problems/" + operationId + "/smpp-connection/not-available")
                    )))
                .doCatch(CannotDetermineTargetSmppConnectionException.class)
                    .log("${exception.stacktrace}")
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.FORBIDDEN.value()))
                    .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                    .setBody(ZalandoProblemDefinition.forbidden(HermesSystemConstants.RestApi.SEND_SHORT_MESSAGE_OPERATION , (builder -> builder
                            .detail("No Smpp Connection capable of sending such message")
                            .type("/problems/" + operationId  + "/cannot-determine-smpp-connection")
                    )))
                .doCatch(CannotSendSmsRequestException.class)
                    .log("${exception.stacktrace}")
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.FORBIDDEN.value()))
                    .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                    .setBody(ZalandoProblemDefinition.forbidden(HermesSystemConstants.RestApi.SEND_SHORT_MESSAGE_OPERATION , (builder -> builder
                            .detail("No Smpp Connection could send the message at the moment")
                            .type("/problems/" + operationId  + "/cannot-send-short-message")
                    )));
    }



}
