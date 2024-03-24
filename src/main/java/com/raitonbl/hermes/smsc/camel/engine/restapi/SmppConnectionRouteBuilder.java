package com.raitonbl.hermes.smsc.camel.engine.restapi;

import com.raitonbl.hermes.smsc.camel.common.HermesConstants;
import com.raitonbl.hermes.smsc.camel.common.HermesSystemConstants;
import com.raitonbl.hermes.smsc.camel.model.SmppConnectionDefinition;
import org.apache.camel.Exchange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class SmppConnectionRouteBuilder extends ApiRouteBuilder {
    private static final String RESOURCES_URI = "/smpp-connections";
    private static final String RESOURCE_URI = RESOURCES_URI + "/{" + HermesConstants.ENTITY_ID + "}";

    @Override
    public void configure() {
        withCreateSmppConnection();
        withGetSmppConnectionById();
        withGetGetSmppConnections();
        withUpdateSmppConnectionById();
        withRemoveGetSmppConnectionById();

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
                        .removeHeaders("*")
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.OK.value()))
        ).routeId(HermesSystemConstants.RestApi.GET_SMPP_CONNECTION_RESTAPI_ROUTE);
    }

    public void withUpdateSmppConnectionById() {
        withPutEndpoint(Opts.builder()
                        .schemaURI("smpp-connection.edit").inputType(SmppConnectionDefinition.class)
                        .serverURI(RESOURCE_URI).operationId(HermesSystemConstants.RestApi.UPDATE_SMPP_CONNECTION_BY_ID_OPERATION)
                        .consumes(new MediaType[]{MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
                        .build(),
                routeDefinition -> routeDefinition
                        // TODO FETCH AND UPDATE FROM REPO
                        .to(HermesSystemConstants.CrudOperations.DIRECT_TO_EDIT_SMPP_CONNECTION)
                        .removeHeaders("*")
                        .setBody(simple(null))
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.NO_CONTENT.value()))
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



}
