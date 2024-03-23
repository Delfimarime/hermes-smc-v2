package com.raitonbl.hermes.smsc.camel.engine.restapi;

import com.raitonbl.hermes.smsc.camel.model.PolicyDefinition;
import com.raitonbl.hermes.smsc.camel.common.HermesConstants;
import com.raitonbl.hermes.smsc.camel.common.HermesSystemConstants;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.component.jsonvalidator.JsonValidationException;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.model.dataformat.YAMLDataFormat;
import org.apache.camel.model.dataformat.YAMLLibrary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.HttpMediaTypeNotSupportedException;

import java.util.Optional;

@Component
public class PolicyRestApiRouteBuilder extends RestapiRouteBuilder {
    private static final String RESOURCES_URI = "/policies";
    private static final String RESOURCE_URI = RESOURCES_URI + "/{" + HermesConstants.ENTITY_ID + "}";

    @Override
    public void configure() {
        withAddPolicyOperationRoute();
        withGetPolicyByIdOperationRoute();
        withGetPoliciesOperationRoute();
        withUpdatePolicyByIdOperationRoute();
        withRemovePolicyOperationRoute();
    }

    private void withAddPolicyOperationRoute() {
        withPostEndpoint(Opts.builder()
                        .serverURI(RESOURCES_URI).operationId(HermesSystemConstants.ADD_POLICIES_OPERATION)
                        .schemaURI("policy").inputType(PolicyDefinition.class)
                        .consumes(new MediaType[]{MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
                        .build(),
                routeDefinition -> routeDefinition
                        .to(HermesSystemConstants.DIRECT_TO_ADD_POLICIES_FROM_DATASOURCE_SYSTEM_ROUTE)
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.OK.value()))
        ).routeId(HermesSystemConstants.CREATE_POLICY_RESTAPI_ROUTE);
    }

    private void withGetPolicyByIdOperationRoute() {
        withGetEndpoint(Opts.builder()
                        .serverURI(RESOURCE_URI).operationId(HermesSystemConstants.GET_POLICY_BY_ID_OPERATION)
                        .consumes(new MediaType[]{MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
                        .build(),
                routeDefinition -> routeDefinition
                        .to(HermesSystemConstants.DIRECT_TO_FIND_POLICY_BY_ID_FROM_DATASOURCE_SYSTEM_ROUTE)
                        .removeHeaders("*")
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.OK.value()))
        ).routeId(HermesSystemConstants.GET_POLICY_RESTAPI_ROUTE);
    }

    public void withUpdatePolicyByIdOperationRoute() {
        withPutEndpoint(Opts.builder()
                        .schemaURI("policy").inputType(PolicyDefinition.class)
                        .serverURI(RESOURCE_URI).operationId(HermesSystemConstants.UPDATE_POLICY_BY_ID_OPERATION)
                        .consumes(new MediaType[]{MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
                        .build(),
                routeDefinition -> routeDefinition
                        .to(HermesSystemConstants.DIRECT_TO_UPDATE_POLICY_ON_DATASOURCE_ROUTE)
                        .removeHeaders("*")
                        .setBody(simple(null))
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.NO_CONTENT.value()))
        ).routeId(HermesSystemConstants.UPDATE_POLICIES_RESTAPI_ROUTE);
    }

    private void withRemovePolicyOperationRoute() {
        withGetEndpoint(Opts.builder()
                        .serverURI(RESOURCE_URI).operationId(HermesSystemConstants.DELETE_POLICY_BY_ID_OPERATION)
                        .consumes(new MediaType[]{MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
                        .build(),
                routeDefinition -> routeDefinition
                        .to(HermesSystemConstants.DIRECT_TO_DELETE_POLICY_BY_ID_ON_DATASOURCE_ROUTE))
                .routeId(HermesSystemConstants.REMOVE_POLICY_RESTAPI_ROUTE);
    }

    private void withGetPoliciesOperationRoute() {

        withGetEndpoint(Opts.builder()
                        .serverURI(RESOURCES_URI).operationId(HermesSystemConstants.GET_POLICIES_OPERATION)
                        .consumes(new MediaType[]{MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
                        .build(),
                routeDefinition -> routeDefinition
                        .to(HermesSystemConstants.DIRECT_TO_FIND_POLICIES_FROM_DATASOURCE_SYSTEM_ROUTE)
                        .removeHeaders("*")
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.OK.value()))
        ).routeId(HermesSystemConstants.GET_POLICY_RESTAPI_ROUTE);
    }

}
