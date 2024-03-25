package com.raitonbl.hermes.smsc.camel.engine.restapi;

import com.raitonbl.hermes.smsc.camel.common.HermesConstants;
import com.raitonbl.hermes.smsc.camel.common.HermesSystemConstants;
import com.raitonbl.hermes.smsc.camel.engine.datasource.EntityNotFoundException;
import com.raitonbl.hermes.smsc.camel.engine.smpp.SmppConnectionNotFoundException;
import com.raitonbl.hermes.smsc.camel.model.PolicyDefinition;
import com.raitonbl.hermes.smsc.camel.model.SmppConnectionDefinition;
import org.apache.camel.Exchange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class SmppConnectionRouteBuilder extends ApiRouteBuilder {
    private static final String RESOURCES_URI = "/smpp-connections";
    private static final String RESOURCE_URI = RESOURCES_URI + "/{" + HermesConstants.ENTITY_ID + "}";
    private static final String SEND_SHORT_MESSAGE_RESOURCE_URI = RESOURCE_URI+"/short-messages";

    @Override
    public void configure() {
        withCreateSmppConnection();
        withGetSmppConnectionById();
        withGetGetSmppConnections();
        withUpdateSmppConnectionById();
        withRemoveGetSmppConnectionById();
        withSendShortMessageById();

    }

    private void withCreateSmppConnection() {
        withPostEndpoint(Opts.builder()
                        .serverURI(RESOURCES_URI).operationId(HermesSystemConstants.RestApi.ADD_SMPP_CONNECTION_OPERATION)
                        .schemaURI("smpp_connection").inputType(SmppConnectionDefinition.class)
                        .consumes(new MediaType[]{MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
                        .build(),
                routeDefinition -> routeDefinition
                        .to(HermesSystemConstants.CrudOperations.DIRECT_TO_ADD_SMPP_CONNECTION)
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.OK.value()))
        ).routeId(HermesSystemConstants.RestApi.CREATE_SMPP_CONNECTION_RESTAPI_ROUTE);
    }

    private void withGetSmppConnectionById() {
        withGetEndpoint(Opts.builder()
                        .serverURI(RESOURCE_URI).operationId(HermesSystemConstants.RestApi.GET_SMPP_CONNECTION_BY_ID_OPERATION)
                        .consumes(new MediaType[]{MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
                        .build(),
                routeDefinition -> routeDefinition
                        .to(HermesSystemConstants.CrudOperations.DIRECT_TO_FIND_SMPP_CONNECTION_BY_ID)
                        .process(exchange -> {
                            if (exchange.getIn().getBody(SmppConnectionDefinition.class) == null) {
                                exchange.setException(new SmppConnectionNotFoundException(
                                        exchange.getIn().getHeader(HermesConstants.ENTITY_ID, String.class)
                                ));
                            }
                        })
                        .removeHeaders("*")
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.OK.value())),
                catchDefinition -> catchDefinition
                        .doCatch(EntityNotFoundException.class)
                            .log("${exception.stacktrace}")
                            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(404))
                            .process(this::setProblemContentTypeIfApplicable)
                            .setBody(ZalandoProblemDefinition.notFound())
        ).routeId(HermesSystemConstants.RestApi.GET_SMPP_CONNECTION_RESTAPI_ROUTE);
    }

    public void withUpdateSmppConnectionById() {
        withPutEndpoint(Opts.builder()
                        .schemaURI("smpp-connection.edit").inputType(SmppConnectionDefinition.class)
                        .serverURI(RESOURCE_URI).operationId(HermesSystemConstants.RestApi.UPDATE_SMPP_CONNECTION_BY_ID_OPERATION)
                        .consumes(new MediaType[]{MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
                        .build(),
                routeDefinition -> routeDefinition
                        .enrich(HermesSystemConstants.CrudOperations.DIRECT_TO_FIND_SMPP_CONNECTION_BY_ID,(exchange,fromComponent)->{
                            SmppConnectionDefinition definition = fromComponent.getIn().getBody(SmppConnectionDefinition.class);
                            if (definition == null) {
                                exchange.setException(new SmppConnectionNotFoundException(
                                        exchange.getIn().getHeader(HermesConstants.ENTITY_ID, String.class)
                                ));
                                return exchange;
                            }
                            exchange.getIn().getBody(SmppConnectionDefinition.class).setAlias(definition.getAlias());
                            return exchange;
                        })
                        .to(HermesSystemConstants.CrudOperations.DIRECT_TO_EDIT_SMPP_CONNECTION)
                        .removeHeaders("*")
                        .setBody(simple(null))
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.NO_CONTENT.value())),
                catchDefinition -> catchDefinition
                        .doCatch(EntityNotFoundException.class)
                            .log("${exception.stacktrace}")
                            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(404))
                            .process(this::setProblemContentTypeIfApplicable)
                            .setBody(ZalandoProblemDefinition.notFound())
        ).routeId(HermesSystemConstants.RestApi.UPDATE_SMPP_CONNECTION_RESTAPI_ROUTE);
    }

    private void withRemoveGetSmppConnectionById() {
        withDeleteEndpoint(Opts.builder()
                        .serverURI(RESOURCE_URI).operationId(HermesSystemConstants.RestApi.DELETE_SMPP_CONNECTION_BY_ID_OPERATION)
                        .consumes(new MediaType[]{MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
                        .build(),
                routeDefinition -> routeDefinition
                        .to(HermesSystemConstants.CrudOperations.DIRECT_TO_DELETE_SMPP_CONNECTION_BY_ID)
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.NO_CONTENT.value()))
        ).routeId(HermesSystemConstants.RestApi.REMOVE_SMPP_CONNECTION_RESTAPI_ROUTE);
    }

    private void withGetGetSmppConnections() {
        withGetEndpoint(Opts.builder()
                        .serverURI(RESOURCES_URI).operationId(HermesSystemConstants.RestApi.GET_SMPP_CONNECTIONS_OPERATION)
                        .consumes(new MediaType[]{MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
                        .build(),
                routeDefinition -> routeDefinition
                        .to(HermesSystemConstants.CrudOperations.DIRECT_TO_GET_SMPP_CONNECTIONS)
                        .removeHeaders("*")
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.OK.value()))
        ).routeId(HermesSystemConstants.RestApi.GET_SMPP_CONNECTIONS_RESTAPI_ROUTE);
    }

    private void withSendShortMessageById(){
        withPostEndpoint(Opts.builder()
                .serverURI(SEND_SHORT_MESSAGE_RESOURCE_URI)
                        .operationId(HermesSystemConstants.RestApi.
                                SEND_SHORT_MESSAGE_THROUGH_SMPP_CONNECTION_OPERATION)
                .schemaURI("short-message").inputType(PolicyDefinition.class)
                        .consumes(new MediaType[]{MediaType.APPLICATION_JSON})
                .build(),
                SendShortMessageHelper::with,
                catchDefinition -> SendShortMessageHelper
                        .andCatch(HermesSystemConstants.
                                RestApi.SEND_SHORT_MESSAGE_THROUGH_SMPP_CONNECTION_OPERATION, catchDefinition))
                .routeId(HermesSystemConstants.
                        RestApi.SEND_SHORT_MESSAGE_THROUGH_SMPP_CONNECTION_OPERATION_RESTAPI_ROUTE);
    }

}
