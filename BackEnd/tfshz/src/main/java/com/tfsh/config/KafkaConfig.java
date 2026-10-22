package com.tfsh.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * Kafka 配置：秒杀订单异步落库。
 *
 * 三个关键决策：
 *   1. **AckMode.RECORD**：每处理完一条就提交 offset。批提交（BATCH/TIME）一旦批次中间失败，
 *      要么整批重复消费、要么前面的消息被误提交丢失，重试无法精确定位失败消息；
 *   2. **DefaultErrorHandler + 指数退避（1s/2s/4s，最多 3 次）**：可重试异常（锁超时、DB 抖动）自动重投，
 *      耗尽后由 DeadLetterPublishingRecoverer 转发到死信 topic，并提交原消息 offset，放行同分区后续消息；
 *   3. **addNotRetryableExceptions**：消息格式错误/字段缺失这类重试无意义的异常，直接进死信，不浪费重试次数。
 */
@Configuration
public class KafkaConfig {

    /** 秒杀订单主 topic */
    public static final String TOPIC_SECKILL_ORDER = "seckill-order";
    /** 死信 topic：重试耗尽或不可重试的消息 */
    public static final String TOPIC_SECKILL_DLQ = "seckill-order-dlq";

    @Bean
    public NewTopic seckillOrderTopic(@Value("${seckill.kafka.partitions:3}") int partitions) {
        return TopicBuilder.name(TOPIC_SECKILL_ORDER).partitions(partitions).replicas(1).build();
    }

    @Bean
    public NewTopic seckillOrderDlqTopic() {
        return TopicBuilder.name(TOPIC_SECKILL_DLQ).partitions(1).replicas(1).build();
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        // 转发到死信 topic；同一 topic 只用一个分区，保证死信里的时间顺序
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition(TOPIC_SECKILL_DLQ, 0));

        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(1_000L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(4_000L);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        // 不可重试：消息体本身有问题，重试多少次都一样
        handler.addNotRetryableExceptions(IllegalArgumentException.class, NullPointerException.class);
        // 转发死信成功后提交原 offset，避免这条消息把同分区后面的消息堵死
        handler.setCommitRecovered(true);
        return handler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> seckillListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            DefaultErrorHandler errorHandler,
            @Value("${seckill.kafka.concurrency:2}") int concurrency) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(concurrency);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
