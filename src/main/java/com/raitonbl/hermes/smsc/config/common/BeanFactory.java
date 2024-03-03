package com.raitonbl.hermes.smsc.config.common;

import com.ctc.wstx.shaded.msv_core.util.Uri;
import com.raitonbl.hermes.smsc.config.HermesConfiguration;
import org.apache.camel.spring.boot.util.ConditionalOnHierarchicalProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

import java.net.URI;
import java.util.Optional;

@Configuration
public class BeanFactory {
    public static final String AWS_S3_CLIENT ="amazonS3Client";
    public static final String AWS_SQS_CLIENT ="amazonSQSClient";
    public static final String RULES_REDIS_CONNECTION_FACTORY ="rulesRedisConnectionFactory";

    @Bean(AWS_SQS_CLIENT)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spring.boot.hermes",name = {"listen-to.type","publish-to.type"}, havingValue = "AWS_SQS")
    public SqsClient getSqsClient() {
        Region region = Optional.ofNullable(System.getenv("DEFAULT_AWS_REGION"))
                .map(Region::of).orElse(Region.AF_SOUTH_1);
        SqsClientBuilder builder = SqsClient.builder().region(region)
                .credentialsProvider(DefaultCredentialsProvider.create());
        Optional.ofNullable(System.getenv("AWS_ENDPOINT_URL"))
                .map(URI::create).ifPresent(builder::endpointOverride);
        return builder.build();
    }

    @Bean(AWS_S3_CLIENT)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "spring.boot.hermes.rules-datasource.type", havingValue = "s3")
    public S3Client getS3Client() {
        Region region = Optional.ofNullable(System.getenv("DEFAULT_AWS_REGION"))
                .map(Region::of).orElse(Region.AF_SOUTH_1);
        S3ClientBuilder builder = S3Client.builder().region(region)
                .credentialsProvider(DefaultCredentialsProvider.create());
        Optional.ofNullable(System.getenv("AWS_ENDPOINT_URL"))
                .map(URI::create).ifPresent(builder::endpointOverride);
        return builder.build();
    }

    @ConditionalOnMissingBean
    @Bean(RULES_REDIS_CONNECTION_FACTORY)
    @ConditionalOnProperty(name = "spring.boot.hermes.rules-datasource.type", havingValue = "redis")
    public RedisConnectionFactory redisConnectionFactory(HermesConfiguration configuration) {
        RedisStandaloneConfiguration cfg = new RedisStandaloneConfiguration(
                configuration.getRulesDatasource().getHost(), configuration.getRulesDatasource().getPort()
        );
        Optional.ofNullable(configuration.getRulesDatasource().getUsername()).ifPresent(cfg::setUsername);
        Optional.ofNullable(configuration.getRulesDatasource().getPassword()).ifPresent(cfg::setPassword);
        Optional.ofNullable(configuration.getRulesDatasource().getDatabase()).ifPresent(cfg::setDatabase);
        return new LettuceConnectionFactory(cfg);
    }

}
