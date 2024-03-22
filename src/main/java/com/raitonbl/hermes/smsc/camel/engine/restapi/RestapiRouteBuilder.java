package com.raitonbl.hermes.smsc.camel.engine.restapi;

import com.raitonbl.hermes.smsc.camel.common.HermesConstants;
import com.raitonbl.hermes.smsc.camel.common.HermesSystemConstants;
import lombok.Builder;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.direct.DirectConsumerNotAvailableException;
import org.apache.camel.component.jsonvalidator.JsonValidationException;
import org.apache.camel.component.rest.RestConstants;
import org.apache.camel.model.CatchDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.model.dataformat.YAMLDataFormat;
import org.apache.camel.model.dataformat.YAMLLibrary;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public abstract class RestapiRouteBuilder extends RouteBuilder {
    public static final String REST_API_SCHEMA = HermesConstants.HEADER_PREFIX + "RestApiSchema";
    public static final String REST_API_OPERATION = HermesConstants.HEADER_PREFIX + "RestApiOperationId";

    @Builder
    static class Opts {
        String method;
        String serverURI;
        String operationId;
        String schemaURI;
        Class<?> inputType;
        MediaType[] consumes;
    }

    protected ProcessorDefinition<?> withGetEndpoint(Opts.OptsBuilder builder, Consumer<ProcessorDefinition<?>> exec) {
        return withGetEndpoint(builder, exec, null);
    }

    protected ProcessorDefinition<?> withGetEndpoint(Opts.OptsBuilder builder, Consumer<ProcessorDefinition<?>> exec, Consumer<CatchDefinition> onDoCatch) {
        return withEndpoint(Opts.builder().method("GET").serverURI(builder.serverURI).operationId(builder.operationId).consumes(builder.consumes), exec, onDoCatch);
    }

    protected ProcessorDefinition<?> withPostEndpoint(Opts.OptsBuilder builder, Consumer<ProcessorDefinition<?>> exec) {
        return withGetEndpoint(builder, exec, null);
    }

    protected ProcessorDefinition<?> withPostEndpoint(Opts.OptsBuilder builder, Consumer<ProcessorDefinition<?>> exec, Consumer<CatchDefinition> onDoCatch) {
        return withEndpoint(Opts.builder().method("POST").schemaURI(builder.schemaURI).inputType(builder.inputType).serverURI(builder.serverURI).operationId(builder.operationId).consumes(builder.consumes), exec, onDoCatch);
    }

    protected ProcessorDefinition<?> withPatchEndpoint(Opts.OptsBuilder builder, Consumer<ProcessorDefinition<?>> exec) {
        return withPatchEndpoint(builder, exec, null);
    }

    protected ProcessorDefinition<?> withPatchEndpoint(Opts.OptsBuilder builder, Consumer<ProcessorDefinition<?>> exec, Consumer<CatchDefinition> onDoCatch) {
        return withEndpoint(Opts.builder().method("PATCH").schemaURI(builder.schemaURI).inputType(builder.inputType).serverURI(builder.serverURI).operationId(builder.operationId).consumes(builder.consumes), exec, onDoCatch);
    }

    protected ProcessorDefinition<?> withPutEndpoint(Opts.OptsBuilder builder, Consumer<ProcessorDefinition<?>> exec) {
        return withPutEndpoint(builder, exec, null);
    }

    protected ProcessorDefinition<?> withPutEndpoint(Opts.OptsBuilder builder, Consumer<ProcessorDefinition<?>> exec, Consumer<CatchDefinition> onDoCatch) {
        return withEndpoint(Opts.builder().method("PATCH").schemaURI(builder.schemaURI).inputType(builder.inputType).serverURI(builder.serverURI).operationId(builder.operationId).consumes(builder.consumes), exec, onDoCatch);
    }

    protected ProcessorDefinition<?> withDeleteEndpoint(Opts.OptsBuilder builder, Consumer<ProcessorDefinition<?>> exec) {
        return withDeleteEndpoint(builder, exec, null);
    }

    protected ProcessorDefinition<?> withDeleteEndpoint(Opts.OptsBuilder builder, Consumer<ProcessorDefinition<?>> exec, Consumer<CatchDefinition> onDoCatch) {
        return withEndpoint(Opts.builder().method("DELETE").schemaURI(builder.schemaURI).inputType(builder.inputType).serverURI(builder.serverURI).operationId(builder.operationId).consumes(builder.consumes), exec, onDoCatch);
    }



    protected ProcessorDefinition<?> withEndpoint(Opts.OptsBuilder builder, Consumer<ProcessorDefinition<?>> exec, Consumer<CatchDefinition> onDoCatch) {
        String method = builder.method;
        String serverURI = builder.serverURI;
        ProcessorDefinition<?> definition = from("rest:" + method + ":" + serverURI)
                .setHeader(REST_API_SCHEMA, constant(builder.schemaURI))
                .setHeader(REST_API_OPERATION, constant(builder.operationId))
                .doTry()
                    .process(exchange -> assertMediaType(builder, exchange))
                    .choice()
                        .when(header(REST_API_SCHEMA).isNotNull())
                            .choice()
                                .when(header(RestConstants.CONTENT_TYPE).isEqualTo(MediaType.TEXT_PLAIN_VALUE))
                                .unmarshal(new YAMLDataFormat()) // Unmarshal from YAML to Java object
                                .marshal().json(JsonLibrary.Jackson)
                                .setBody(simple("${body}"))
                                .convertBodyTo(String.class)
                            .end()
                            .to("json-validator:classpath:schemas/" + builder.schemaURI + ".json?contentCache=true&failOnNullBody=true")
                            .unmarshal().json(JsonLibrary.Jackson, builder.inputType)
                    .end()
                    .enrich(HermesSystemConstants.OPENID_CONNECT_GET_AUTHENTICATION, (original, fromComponent) -> {
                        Optional.ofNullable(fromComponent.getIn().getBody(String.class))
                                .ifPresent(value -> original.getIn().setHeader(HermesConstants.AUTHORIZATION, value));
                        return original;
                    });
        exec.accept(definition);
        CatchDefinition doCatchDefinition = definition
                    .endDoTry()
                    .doCatch(JsonValidationException.class)
                        .log("${exception.stacktrace}")
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.UNPROCESSABLE_ENTITY.value()))
                        .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                        .setBody(ZalandoProblemDefinition.unprocessableEntity(builder.operationId))
                    .doCatch(HttpMediaTypeNotSupportedException.class)
                        .log("${exception.stacktrace}")
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value()))
                        .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                        .setBody(ZalandoProblemDefinition.unsupportedMediaType(builder.operationId))
                    .doCatch(HttpMediaTypeNotAcceptableException.class)
                        .log("${exception.stacktrace}")
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value()))
                        .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                        .setBody(ZalandoProblemDefinition.unsupportedAcceptMediaType(builder.operationId))
                    .doCatch(Exception.class)
                        .log("${exception.stacktrace}")
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
                        .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                        .setBody(ZalandoProblemDefinition.get())
                    .endDoCatch();

        Optional.ofNullable(onDoCatch).ifPresent(each -> each.accept(doCatchDefinition));

        return doCatchDefinition
                .endDoTry()
                .doFinally()
                    .choice()
                        .when(header(RestConstants.ACCEPT).isEqualTo(MediaType.APPLICATION_JSON_VALUE))
                            .marshal().json(JsonLibrary.Jackson)
                        .endChoice()
                        .when(header(RestConstants.ACCEPT).isEqualTo(MediaType.TEXT_PLAIN_VALUE))
                            .marshal().yaml(YAMLLibrary.SnakeYAML)
                        .endChoice()
                    .end()
                .removeHeaders("*", Exchange.HTTP_RESPONSE_CODE, Exchange.CONTENT_TYPE)
                .end();
    }

    private void assertMediaType(Opts.OptsBuilder builder, Exchange exchange) {
        String contentType = Optional
                .ofNullable(exchange.getIn().getHeader(RestConstants.CONTENT_TYPE, String.class))
                .orElse(MediaType.APPLICATION_JSON_VALUE);
        List<MediaType> consumes = builder.consumes == null ?
                List.of(MediaType.APPLICATION_JSON) : Arrays.asList(builder.consumes);
        try {

            MediaType mediaType = MediaType.valueOf(contentType);
            if (consumes.stream().noneMatch(mediaType::isCompatibleWith)) {
                exchange.setException(new HttpMediaTypeNotSupportedException(contentType));
            }
        } catch (InvalidMediaTypeException ex) {
            exchange.setException(new HttpMediaTypeNotSupportedException(contentType));
        }
        String acceptType = Optional
                .ofNullable(exchange.getIn().getHeader(RestConstants.ACCEPT, String.class))
                .orElse(contentType);
        try {
            MediaType acceptMediaType = MediaType.valueOf(acceptType);
            if (consumes.stream().noneMatch(acceptMediaType::isCompatibleWith)) {
                exchange.setException(new HttpMediaTypeNotAcceptableException(acceptType));
            }

        } catch (InvalidMediaTypeException ex) {
            exchange.setException(new HttpMediaTypeNotSupportedException(contentType));
        }
        exchange.getIn().setHeader(RestConstants.ACCEPT, acceptType);
        exchange.getIn().setHeader(RestConstants.CONTENT_TYPE, contentType);
    }

}
