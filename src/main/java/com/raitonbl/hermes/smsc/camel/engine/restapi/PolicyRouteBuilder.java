package com.raitonbl.hermes.smsc.camel.engine.restapi;

import com.raitonbl.hermes.smsc.camel.engine.datasource.EntityNotFoundException;
import com.raitonbl.hermes.smsc.camel.engine.smpp.PolicyNotFoundException;
import com.raitonbl.hermes.smsc.camel.engine.smpp.SmppConnectionNotFoundException;
import com.raitonbl.hermes.smsc.camel.model.PolicyDefinition;
import com.raitonbl.hermes.smsc.camel.common.HermesConstants;
import com.raitonbl.hermes.smsc.camel.common.HermesSystemConstants;
import com.raitonbl.hermes.smsc.camel.model.SmppConnectionDefinition;
import org.apache.camel.Exchange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class PolicyRouteBuilder extends ApiRouteBuilder {
    private static final String RESOURCES_URI = "/policies";
    private static final String RESOURCE_URI = RESOURCES_URI + "/{" + HermesConstants.ENTITY_ID + "}";

    @Override
    public void configure() {
        withGetPolicies();
        withCreatePolicy();
        withGetPolicyById();
        withUpdatePolicyById();
        withRemovePolicyById();
    }

    private void withCreatePolicy() {
        withPostEndpoint(Opts.builder()
                        .serverURI(RESOURCES_URI).operationId(HermesSystemConstants.RestApi.ADD_POLICIES_OPERATION)
                        .schemaURI("policy").inputType(PolicyDefinition.class)
                        .consumes(new MediaType[]{MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
                        .build(),
                routeDefinition -> routeDefinition
                        .to(HermesSystemConstants.CrudOperations.DIRECT_TO_ADD_POLICIES)
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.OK.value()))
        ).routeId(HermesSystemConstants.RestApi.CREATE_POLICY_RESTAPI_ROUTE);
    }

    private void withGetPolicyById() {
        withGetEndpoint(Opts.builder()
                        .serverURI(RESOURCE_URI).operationId(HermesSystemConstants.RestApi.GET_POLICY_BY_ID_OPERATION)
                        .consumes(new MediaType[]{MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
                        .build(),
                routeDefinition -> routeDefinition
                        .to(HermesSystemConstants.CrudOperations.DIRECT_TO_FIND_POLICY_BY_ID)
                        .process(exchange -> {
                            if (exchange.getIn().getBody(PolicyDefinition.class) == null) {
                                exchange.setException(new PolicyNotFoundException(
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
        ).routeId(HermesSystemConstants.RestApi.GET_POLICY_RESTAPI_ROUTE);
    }

    public void withUpdatePolicyById() {
        withPutEndpoint(Opts.builder()
                        .schemaURI("policy").inputType(PolicyDefinition.class)
                        .serverURI(RESOURCE_URI).operationId(HermesSystemConstants.RestApi.UPDATE_POLICY_BY_ID_OPERATION)
                        .consumes(new MediaType[]{MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
                        .build(),
                routeDefinition -> routeDefinition
                        .to(HermesSystemConstants.CrudOperations.DIRECT_TO_EDIT_POLICY)
                        .removeHeaders("*")
                        .setBody(simple(null))
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.NO_CONTENT.value())),
                catchDefinition -> catchDefinition
                        .doCatch(EntityNotFoundException.class)
                            .log("${exception.stacktrace}")
                            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(404))
                            .process(this::setProblemContentTypeIfApplicable)
                            .setBody(ZalandoProblemDefinition.notFound())
        ).routeId(HermesSystemConstants.RestApi.UPDATE_POLICIES_RESTAPI_ROUTE);
    }

    private void withRemovePolicyById() {
        withDeleteEndpoint(Opts.builder()
                        .serverURI(RESOURCE_URI).operationId(HermesSystemConstants.RestApi.DELETE_POLICY_BY_ID_OPERATION)
                        .consumes(new MediaType[]{MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
                        .build(),
                routeDefinition -> routeDefinition
                        .to(HermesSystemConstants.CrudOperations.DIRECT_TO_DELETE_POLICY_BY_ID))
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.NO_CONTENT.value())
                ).routeId(HermesSystemConstants.RestApi.REMOVE_POLICY_RESTAPI_ROUTE);
    }

    private void withGetPolicies() {
        withGetEndpoint(Opts.builder()
                        .serverURI(RESOURCES_URI).operationId(HermesSystemConstants.RestApi.GET_POLICIES_OPERATION)
                        .consumes(new MediaType[]{MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
                        .build(),
                routeDefinition -> routeDefinition
                        .to(HermesSystemConstants.CrudOperations.DIRECT_TO_GET_POLICIES)
                        .removeHeaders("*")
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.OK.value()))
        ).routeId(HermesSystemConstants.RestApi.GET_ALL_POLICIES_RESTAPI_ROUTE);
    }

}
