package com.raitonbl.hermes.smsc.camel.engine.restapi;

import com.raitonbl.hermes.smsc.camel.common.HermesConstants;
import com.raitonbl.hermes.smsc.camel.common.HermesSystemConstants;
import lombok.Builder;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jsonvalidator.JsonValidationException;
import org.apache.camel.component.rest.RestConstants;
import org.apache.camel.model.TryDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.TryDefinition;
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
    protected static class Opts {
        String method;
        String serverURI;
        String operationId;
        String schemaURI;
        Class<?> inputType;
        MediaType[] consumes;
    }

    protected ProcessorDefinition<?> withGetEndpoint(Opts opts, Consumer<ProcessorDefinition<?>> exec) {
        return withGetEndpoint(opts, exec, null);
    }

    protected ProcessorDefinition<?> withGetEndpoint(Opts opts, Consumer<ProcessorDefinition<?>> exec, Consumer<TryDefinition> onDoCatch) {
        return withEndpointMethod("GET", Opts.builder().serverURI(opts.serverURI)
                .operationId(opts.operationId).consumes(opts.consumes).build(), exec, onDoCatch);
    }

    protected ProcessorDefinition<?> withPostEndpoint(Opts opts, Consumer<ProcessorDefinition<?>> exec) {
        return withEndpointMethod("POST", opts, exec);
    }

    protected ProcessorDefinition<?> withPostEndpoint(Opts opts, Consumer<ProcessorDefinition<?>> exec, Consumer<TryDefinition> onDoCatch) {
        return withEndpointMethod("POST", opts, exec, onDoCatch);
    }

    protected ProcessorDefinition<?> withPatchEndpoint(Opts opts, Consumer<ProcessorDefinition<?>> exec) {
        return withEndpointMethod("PATCH", opts, exec);
    }

    protected ProcessorDefinition<?> withPatchEndpoint(Opts opts, Consumer<ProcessorDefinition<?>> exec, Consumer<TryDefinition> onDoCatch) {
        return withEndpointMethod("PATCH", opts, exec, onDoCatch);
    }

    protected ProcessorDefinition<?> withPutEndpoint(Opts opts, Consumer<ProcessorDefinition<?>> exec) {
        return withEndpointMethod("PUT", opts, exec);
    }

    protected ProcessorDefinition<?> withPutEndpoint(Opts opts, Consumer<ProcessorDefinition<?>> exec, Consumer<TryDefinition> onDoCatch) {
        return withEndpointMethod("PUT", opts, exec, onDoCatch);
    }

    protected ProcessorDefinition<?> withDeleteEndpoint(Opts opts, Consumer<ProcessorDefinition<?>> exec) {
        return withEndpointMethod("DELETE", opts, exec);
    }

    protected ProcessorDefinition<?> withDeleteEndpoint(Opts opts, Consumer<ProcessorDefinition<?>> exec, Consumer<TryDefinition> onDoCatch) {
        return withEndpointMethod("DELETE", opts, exec, onDoCatch);
    }

    protected ProcessorDefinition<?> withEndpointMethod(String method, Opts opts, Consumer<ProcessorDefinition<?>> exec) {
        return withEndpointMethod(method, opts, exec, null);
    }

    protected ProcessorDefinition<?> withEndpointMethod(String method, Opts opts, Consumer<ProcessorDefinition<?>> exec, Consumer<TryDefinition> onDoCatch) {
        return withEndpoint(Opts.builder().method(method).schemaURI(opts.schemaURI).inputType(opts.inputType)
                .serverURI(opts.serverURI).operationId(opts.operationId).consumes(opts.consumes).build(), exec, onDoCatch);
    }

    protected ProcessorDefinition<?> withEndpoint(Opts opts, Consumer<ProcessorDefinition<?>> exec, Consumer<TryDefinition> onDoCatch) {
        String method = opts.method;
        String serverURI = opts.serverURI;
        ProcessorDefinition<?> definition = from("rest:" + method + ":" + serverURI)
                .setHeader(REST_API_SCHEMA, constant(opts.schemaURI))
                .setHeader(REST_API_OPERATION, constant(opts.operationId))
                .doTry()
                    .process(exchange -> assertMediaType(opts, exchange))
                    .choice()
                        .when(header(REST_API_SCHEMA).isNotNull())
                            .choice()
                                .when(header(RestConstants.CONTENT_TYPE).isEqualTo(MediaType.TEXT_PLAIN_VALUE))
                                .unmarshal(new YAMLDataFormat()) // Unmarshal from YAML to Java object
                                .marshal().json(JsonLibrary.Jackson)
                                .setBody(simple("${body}"))
                                .convertBodyTo(String.class)
                            .end()
                            .to("json-validator:classpath:schemas/" + opts.schemaURI + ".json?contentCache=true&failOnNullBody=true")
                            .unmarshal().json(JsonLibrary.Jackson, opts.inputType)
                    .end()
                    .enrich(HermesSystemConstants.OPENID_CONNECT_GET_AUTHENTICATION, (original, fromComponent) -> {
                        Optional.ofNullable(fromComponent.getIn().getBody(String.class))
                                .ifPresent(value -> original.getIn().setHeader(HermesConstants.AUTHORIZATION, value));
                        return original;
                    });
        exec.accept(definition);
        TryDefinition TryDefinition = definition
                    .endDoTry()
                    .doCatch(JsonValidationException.class)
                        .log("${exception.stacktrace}")
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.UNPROCESSABLE_ENTITY.value()))
                        .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                        .setBody(ZalandoProblemDefinition.unprocessableEntity(opts.operationId))
                    .doCatch(HttpMediaTypeNotSupportedException.class)
                        .log("${exception.stacktrace}")
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value()))
                        .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                        .setBody(ZalandoProblemDefinition.unsupportedMediaType(opts.operationId))
                    .doCatch(HttpMediaTypeNotAcceptableException.class)
                        .log("${exception.stacktrace}")
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value()))
                        .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                        .setBody(ZalandoProblemDefinition.unsupportedAcceptMediaType(opts.operationId))
                    .doCatch(Exception.class)
                        .log("${exception.stacktrace}")
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
                        .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                        .setBody(ZalandoProblemDefinition.get());

        Optional.ofNullable(onDoCatch).ifPresent(each -> each.accept(TryDefinition));

        return TryDefinition
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

    private void assertMediaType(Opts opts, Exchange exchange) {
        String contentType = Optional
                .ofNullable(exchange.getIn().getHeader(RestConstants.CONTENT_TYPE, String.class))
                .orElse(MediaType.APPLICATION_JSON_VALUE);
        List<MediaType> consumes = opts.consumes == null ?
                List.of(MediaType.APPLICATION_JSON) : Arrays.asList(opts.consumes);
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
