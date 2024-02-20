package com.raitonbl.hermes.smsc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

import java.net.URI;
import java.util.Optional;

@Configuration
public class BeanFactory {

    @Bean("amazonSQSClient")
    public SqsClient getSqsClient() {
        Region region = Optional.ofNullable(System.getenv("DEFAULT_AWS_REGION"))
                .map(Region::of)
                .orElseGet(()->Region.AF_SOUTH_1);
        SqsClientBuilder builder = SqsClient.builder().region(region)
                .credentialsProvider(DefaultCredentialsProvider.create());
        Optional.ofNullable(System.getenv("AWS_ENDPOINT_URL"))
                .ifPresent(endpoint -> builder.endpointOverride(URI.create(endpoint)));
        return builder.build();
    }

}
