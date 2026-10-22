package com.tfsh.utils;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.tfsh.utils.RedisConstants.*;

/**
 * 缓存删除补偿器：保证"更新数据库后删除缓存"的最终执行。
 *
 * 原理（第一性）：
 *   1. 删除缓存天然幂等（删两次 = 删一次），所以消息重复消费没有副作用——
 *      这是"补偿"能用消息队列实现的根本原因，也是消息里只放 key、不放数据的原因；
 *   2. 删除失败 -> 写一条消息到 Redis Stream，消费者异步重试；
 *   3. 梯度退避：1s、2s、4s……上限 60s，最多重试 CACHE_DELETE_MAX_RETRY 次；
 *   4. 重试耗尽仍失败 -> 记录 error 日志告警，人工介入（缓存自身的 TTL 是最后兜底）。
 */
@Slf4j
@Component
public class CacheDeleteCompensator {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CONSUMER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct
    private void init() {
        // 创建消费者组（组已存在时 Redis 返回 BUSYGROUP 错误，忽略即可）
        try {
            stringRedisTemplate.opsForStream().createGroup(CACHE_DELETE_STREAM, CACHE_DELETE_GROUP);
        } catch (Exception e) {
            // 组已存在
        }
        CONSUMER_EXECUTOR.submit(new CacheDeleteHandler());
    }

    /**
     * 删除缓存；删除失败（网络抖动等异常）时发送补偿消息，由消费者梯度重试。
     * 注意：key 不存在时 delete 返回 false，同样视为删除成功——缓存本来就不在，目标已达成。
     */
    public void delete(String key) {
        try {
            stringRedisTemplate.delete(key);
        } catch (Exception e) {
            log.error("删除缓存失败，已发送补偿消息：{}", key, e);
            send(key, 0);
        }
    }

    private void send(String key, int retry) {
        Map<String, Object> msg = Map.of(
                "key", key,
                "retry", String.valueOf(retry),
                "ts", String.valueOf(System.currentTimeMillis()));
        stringRedisTemplate.opsForStream().add(
                StreamRecords.newRecord().ofMap(msg).withStreamKey(CACHE_DELETE_STREAM));
    }

    private class CacheDeleteHandler implements Runnable {

        @Override
        public void run() {
            while (true) {
                try {
                    // 1.读取补偿消息 XREADGROUP GROUP g-cache c-cache COUNT 1 BLOCK 2000 STREAMS stream.cache-delete >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from(CACHE_DELETE_GROUP, "c-cache"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(CACHE_DELETE_STREAM, ReadOffset.lastConsumed()));
                    // 2.无消息则继续等待
                    if (list == null || list.isEmpty()) {
                        continue;
                    }
                    // 3.处理消息
                    handle(list.get(0));
                } catch (Exception e) {
                    log.error("消费补偿消息异常", e);
                    handlePending();
                }
            }
        }

        /** 处理消费失败堆积的消息（未被 XACK 的消息会进入 pending-list） */
        private void handlePending() {
            while (true) {
                try {
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from(CACHE_DELETE_GROUP, "c-cache"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(CACHE_DELETE_STREAM, ReadOffset.from("0")));
                    if (list == null || list.isEmpty()) {
                        break;
                    }
                    handle(list.get(0));
                } catch (Exception e) {
                    log.error("处理 pending-list 异常", e);
                    break;
                }
            }
        }

        private void handle(MapRecord<String, Object, Object> record) {
            Map<Object, Object> value = record.getValue();
            String key = (String) value.get("key");
            int retry = Integer.parseInt((String) value.get("retry"));
            try {
                // 删除缓存（key 不存在视为成功）
                stringRedisTemplate.delete(key);
                // 删除成功 -> 确认消息 XACK
                stringRedisTemplate.opsForStream().acknowledge(CACHE_DELETE_STREAM, CACHE_DELETE_GROUP, record.getId());
            } catch (Exception e) {
                if (retry + 1 >= CACHE_DELETE_MAX_RETRY) {
                    // 重试耗尽：告警，人工介入；先 XACK 避免 pending-list 无限堆积
                    log.error("删除缓存重试已达 {} 次，请人工介入：{}", CACHE_DELETE_MAX_RETRY, key, e);
                    stringRedisTemplate.opsForStream().acknowledge(CACHE_DELETE_STREAM, CACHE_DELETE_GROUP, record.getId());
                } else {
                    try {
                        // 梯度退避：1s、2s、4s……上限 60s
                        Thread.sleep(Math.min(1000L << retry, 60_000L));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    // 确认旧消息，发送一条带新重试次数的消息
                    stringRedisTemplate.opsForStream().acknowledge(CACHE_DELETE_STREAM, CACHE_DELETE_GROUP, record.getId());
                    send(key, retry + 1);
                }
            }
        }
    }
}
