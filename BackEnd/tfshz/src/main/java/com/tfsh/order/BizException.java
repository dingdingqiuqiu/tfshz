package com.tfsh.order;

/**
 * 业务异常：消息会由 WebExceptionAdvice 统一转成 Result.fail(errorMsg) 返回给前端。
 * 在 @Transactional 方法里抛出它，之前的扣库存等操作会一起回滚。
 */
public class BizException extends RuntimeException {

    public BizException(String message) {
        super(message);
    }

    public BizException(String message, Throwable cause) {
        super(message, cause);
    }
}
