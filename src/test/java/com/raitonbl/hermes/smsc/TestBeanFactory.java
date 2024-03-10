package com.raitonbl.hermes.smsc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raitonbl.hermes.smsc.camel.asyncapi.SendSmsRequest;
import com.raitonbl.hermes.smsc.config.BeanFactory;
import com.raitonbl.hermes.smsc.config.policy.Rule;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import software.amazon.awssdk.utils.StringInputStream;

import javax.cache.Cache;
import javax.cache.Caching;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;

@Configuration
public class TestBeanFactory {
    public static final String QUEUE_URL_PREFIX = "https://sqs.af-south-1.localhost.localstack.cloud:4566/000000000000";
    public static List<String> queueNames = List.of("send-sms-queue", "received-sms-queue", "unsupported-pdu-queue", "delivery-receipt-queue");
    public static List<SendSmsRequest> requestQueue = new ArrayList<>();
    public static List<Rule> ruleDefinition = null;


    public static void setRules(List<Rule> definition) {
        Optional.ofNullable(Caching.getCachingProvider().getCacheManager().getCache(RuleRouteBuilder.CACHE_NAME))
                .ifPresent(Cache::clear);
        TestBeanFactory.ruleDefinition = definition;
    }

    @Primary
    @Bean(BeanFactory.AWS_SQS_CLIENT)
    public SqsClient getSqsClient(ObjectMapper objectMapper) throws Exception {
        SqsClient sqsClient = Mockito.mock(SqsClient.class);
        // LIST QUEUE
        ListQueuesResponse sqsListQueueResponse = ListQueuesResponse.builder()
                .queueUrls(queueNames.stream().map(v -> QUEUE_URL_PREFIX + "/" + v).toArray(String[]::new))
                .build();
        Answer<?> onSqsListQueue = (iv) -> sqsListQueueResponse;
        Mockito.when(sqsClient.listQueues()).then(onSqsListQueue);
        Mockito.when(sqsClient.listQueues(any(Consumer.class))).then(onSqsListQueue);
        Mockito.when(sqsClient.listQueues(any(ListQueuesRequest.class))).then(onSqsListQueue);

        // RECEIVE MESSAGE
        Message[] seq = new Message[requestQueue.size()];
        for (int i = 0; i < requestQueue.size(); i++) {
            seq[i] = Message.builder().messageId(UUID.randomUUID().toString())
                    .body(objectMapper.writeValueAsString(requestQueue.get(i)))
                    .receiptHandle(UUID.randomUUID().toString()).build();
        }
        AtomicBoolean isFirstRequest = new AtomicBoolean(true);
        Answer<?> onSqsReceiveMessage = (iv) -> {
            if (isFirstRequest.get()) {
                isFirstRequest.set(false);
                return ReceiveMessageResponse.builder().messages(seq).build();
            }
            return ReceiveMessageResponse.builder().build();
        };
        Mockito
                .when(sqsClient.receiveMessage(any(Consumer.class)))
                .then(onSqsReceiveMessage);
        Mockito
                .when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .then(onSqsReceiveMessage);

        // SEND BATCH

        // DELETE MESSAGE

        return sqsClient;
    }

    @Primary
    @Bean(BeanFactory.AWS_S3_CLIENT)
    @ConditionalOnProperty(name = "spring.boot.hermes.rules-datasource.type", havingValue = "s3")
    public S3Client getS3Client(ObjectMapper objectMapper) throws Exception {
        S3Client s3Client = Mockito.mock(S3Client.class);
        Answer<?> onS3GetObject = (iv) -> {
            if (ruleDefinition == null) {
                throw NoSuchKeyException.builder().build();
            }
            String content = objectMapper.writeValueAsString(ruleDefinition);
            final StringInputStream inputStream = Optional.ofNullable(content).map(StringInputStream::new).orElse(null);
            return new ResponseInputStream<>(GetObjectResponse.builder()
                    .contentType(MediaType.APPLICATION_JSON_VALUE).build(), inputStream);
        };
        Mockito.when(s3Client.getObject(any(GetObjectRequest.class), any(ResponseTransformer.class))).then(onS3GetObject);
        return s3Client;
    }

}
