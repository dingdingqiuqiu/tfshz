package com.tfsh.seckill;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 秒杀链路指标（Prometheus 可直接抓取）。
 *
 * 三个关键指标：
 *   - seckill_requests_total{result}：入口分流（成功/库存不足/重复/未开始/已结束/投递失败…）
 *     —— 用来回答"抢购到底是被什么挡下的"，比只看成功率有用；
 *   - seckill_order_persist_total{outcome}：消费端落库结果（含幂等跳过）；
 *   - seckill_dead_letter_total{reason}：死信数量，> 0 就应该告警。
 *
 * 标签值都是**固定枚举**，不用 userId / orderId，避免基数爆炸。
 */
@Component
public class SeckillMetrics {

    public static final String RESULT_SUCCESS = "success";
    public static final String RESULT_STOCK_EMPTY = "stock_empty";
    public static final String RESULT_DUPLICATE = "duplicate";
    public static final String RESULT_NOT_STARTED = "not_started";
    public static final String RESULT_ENDED = "ended";
    public static final String RESULT_NOT_WARMED = "not_warmed";
    public static final String RESULT_PUBLISH_FAILED = "publish_failed";
    public static final String RESULT_LUA_ERROR = "lua_error";
    public static final String RESULT_FALLBACK_SYNC = "fallback_sync";

    private static final String[] REQUEST_RESULTS = {
            RESULT_SUCCESS, RESULT_STOCK_EMPTY, RESULT_DUPLICATE, RESULT_NOT_STARTED,
            RESULT_ENDED, RESULT_NOT_WARMED, RESULT_PUBLISH_FAILED, RESULT_LUA_ERROR, RESULT_FALLBACK_SYNC
    };
    private static final String[] PERSIST_OUTCOMES = {"success", "already_exists", "user_duplicate", "stock_exhausted", "invalid"};
    private static final String[] DLQ_REASONS = {"duplicate", "stock_exhausted", "invalid"};

    private final Map<String, Counter> requests = new HashMap<>();
    private final Map<String, Counter> persists = new HashMap<>();
    private final Map<String, Counter> deadLetters = new HashMap<>();

    public SeckillMetrics(MeterRegistry registry) {
        for (String result : REQUEST_RESULTS) {
            requests.put(result, Counter.builder("seckill.requests")
                    .description("秒杀请求按结果分类")
                    .tag("result", result)
                    .register(registry));
        }
        for (String outcome : PERSIST_OUTCOMES) {
            persists.put(outcome, Counter.builder("seckill.order.persist")
                    .description("消费端订单落库结果")
                    .tag("outcome", outcome)
                    .register(registry));
        }
        for (String reason : DLQ_REASONS) {
            deadLetters.put(reason, Counter.builder("seckill.dead.letter")
                    .description("进入死信的消息数")
                    .tag("reason", reason)
                    .register(registry));
        }
    }

    public void request(String result) {
        Counter counter = requests.get(result);
        if (counter != null) {
            counter.increment();
        }
    }

    public void persist(PersistOutcome outcome) {
        String key = switch (outcome) {
            case SUCCESS -> "success";
            case ALREADY_EXISTS -> "already_exists";
            case USER_DUPLICATE -> "user_duplicate";
            case STOCK_EXHAUSTED -> "stock_exhausted";
            default -> "invalid";
        };
        Counter counter = persists.get(key);
        if (counter != null) {
            counter.increment();
        }
    }

    public void deadLetter(String reason) {
        Counter counter = deadLetters.get(reason);
        if (counter != null) {
            counter.increment();
        }
    }
}
