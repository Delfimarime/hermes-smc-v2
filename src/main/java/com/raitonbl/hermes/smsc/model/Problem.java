package com.raitonbl.hermes.smsc.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.apache.camel.builder.ValueBuilder;
import org.springframework.http.HttpStatus;

@Getter
@Setter
@Builder
public class Problem {
    private String type;
    private String title;
    private int status;
    private String detail;

    public static ValueBuilder get() {
        return org.apache.camel.builder.Builder.constant(Problem.builder()
                .detail("An error occurred during your request :(, " +
                        "retry and if it persist contact the administrators")
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .title("Something went wrong").type("/problems").build());
    }

    public static ValueBuilder unprocessableEntity(String operationId) {
        return org.apache.camel.builder.Builder.constant(Problem.builder()
                .detail("Request doesn't match the specification")
                .status(HttpStatus.UNPROCESSABLE_ENTITY.value()).title("Unprocessable entity")
                .type("/problems/" + operationId + "/unprocessable-entity").build());
    }

    public static ValueBuilder unsupportedMediaType(String operationId) {
        return org.apache.camel.builder.Builder.constant(Problem.builder()
                .detail("Request doesn't match the specification")
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value()).title("Unsupported Media Type")
                .type("/problems/" + operationId + "/unsupported-media-type").build());
    }
}
