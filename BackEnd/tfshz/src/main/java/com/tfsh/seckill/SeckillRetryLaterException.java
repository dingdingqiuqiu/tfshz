package com.tfsh.seckill;

/**
 * 可重试异常：表示“现在落不了库，但等一会儿可能就行”（抢锁超时、DB 连接抖动等）。
 *
 * 消费者捕获它之后 **不能 XACK**，消息会留在 Pending List 里，
 * 由认领任务在空闲超时后重新 XCLAIM 回来重试；投递次数超过上限再进死信。
 */
public class SeckillRetryLaterException extends RuntimeException {

    public SeckillRetryLaterException(String message) {
        super(message);
    }

    public SeckillRetryLaterException(String message, Throwable cause) {
        super(message, cause);
    }
}
