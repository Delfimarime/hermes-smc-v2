package com.raitonbl.hermes.smsc.config;

import com.raitonbl.hermes.smsc.camel.common.HermesSystemConstants;
import com.raitonbl.hermes.smsc.config.repository.AuthenticationType;
import com.raitonbl.hermes.smsc.config.repository.DatasourceConfiguration;
import org.apache.camel.component.etcd3.Etcd3Configuration;
import org.apache.camel.component.etcd3.Etcd3Constants;
import org.apache.camel.component.jcache.JCacheConstants;
import org.apache.camel.component.jcache.policy.JCachePolicy;
import org.apache.camel.model.language.SimpleExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Configuration
public class BeanFactory {
    public static final String AWS_S3_CLIENT ="amazonS3Client";
    public static final String AWS_SQS_CLIENT ="amazonSQSClient";
    public static final String ETCD_V3_CONFIGURATION ="Etcd3Configuration";
    public static final String RULES_REDIS_CONNECTION_FACTORY ="rulesRedisConnectionFactory";

    @ConditionalOnMissingBean
    @Bean(ETCD_V3_CONFIGURATION)
    @ConditionalOnProperty(name = "spring.boot.hermes.datasource.type", havingValue = "etcd")
    public Etcd3Configuration etcd3Configuration(HermesConfiguration hermesCfg) {
        DatasourceConfiguration configuration = hermesCfg.getDatasource();
        Etcd3Configuration etcd3Configuration = new Etcd3Configuration();
        String[] endpoints = configuration.getEndpoint();
        if(endpoints ==null){
            endpoints = new String[]{"http://localhost:2379"};
        }
        etcd3Configuration.setEndpoints(endpoints);
        if (AuthenticationType.BASIC_AUTH.equals(configuration.getAuthenticationType())) {
            etcd3Configuration.setPassword(Optional.ofNullable(configuration.getPassword())
                    .orElseThrow(() -> new IllegalArgumentException("password is required")));
            etcd3Configuration.setUserName(Optional.ofNullable(configuration.getUsername())
                    .orElseThrow(() -> new IllegalArgumentException("username is required")));
        } else if (!AuthenticationType.NONE.equals(configuration.getAuthenticationType())) {
            throw new IllegalArgumentException(configuration.getAuthenticationType() + " isn't supported");
        }
        Optional.ofNullable(configuration.getConnectionTimeout()).map(java.time.Duration::ofSeconds)
                .ifPresent(etcd3Configuration::setConnectionTimeout);
        Optional.ofNullable(configuration.getKeepAliveTime()).map(java.time.Duration::ofSeconds)
                .ifPresent(etcd3Configuration::setKeepAliveTime);
        Optional.ofNullable(configuration.getKeepAliveTimeout()).map(java.time.Duration::ofSeconds)
                .ifPresent(etcd3Configuration::setKeepAliveTimeout);
        Optional.ofNullable(configuration.getRetryMaxDelay()).map(java.time.Duration::ofSeconds)
                .ifPresent(etcd3Configuration::setRetryMaxDuration);
        Optional.ofNullable(configuration.getFromIndex()).ifPresent(etcd3Configuration::setFromIndex);
        Optional.ofNullable(configuration.getNamespace()).ifPresent(etcd3Configuration::setNamespace);
        Optional.ofNullable(configuration.getRetryDelay()).ifPresent(etcd3Configuration::setRetryDelay);
        Optional.ofNullable(configuration.getServicePath()).ifPresent(etcd3Configuration::setServicePath);
        Optional.ofNullable(configuration.getRetryMaxDelay()).ifPresent(etcd3Configuration::setRetryMaxDelay);
        return etcd3Configuration;
    }

    @Bean(HermesSystemConstants.KV_CACHE_NAME)
    public JCachePolicy kvJCachePolicy() {
        JCachePolicy jCachePolicy = new JCachePolicy();
        MutableConfiguration<String, Object> configuration = new MutableConfiguration<>();
        configuration.setTypes(String.class, Object.class);
        configuration
                .setExpiryPolicyFactory(CreatedExpiryPolicy
                        .factoryOf(new Duration(TimeUnit.MINUTES, 3)));
        CacheManager cacheManager = Caching.getCachingProvider().getCacheManager();
        Cache<String, Object> cache = cacheManager.createCache(HermesSystemConstants.KV_CACHE_NAME, configuration);
        jCachePolicy.setCache(cache);
        jCachePolicy.setCacheManager(cacheManager);
        jCachePolicy.setKeyExpression( new SimpleExpression("${headers."+ JCacheConstants.KEY +"}"));
        return jCachePolicy;
    }

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
                configuration.getPolicyRepository().getHost(), configuration.getPolicyRepository().getPort()
        );
        Optional.ofNullable(configuration.getPolicyRepository().getUsername()).ifPresent(cfg::setUsername);
        Optional.ofNullable(configuration.getPolicyRepository().getPassword()).ifPresent(cfg::setPassword);
        Optional.ofNullable(configuration.getPolicyRepository().getDatabase()).ifPresent(cfg::setDatabase);
        return new LettuceConnectionFactory(cfg);
    }

}
