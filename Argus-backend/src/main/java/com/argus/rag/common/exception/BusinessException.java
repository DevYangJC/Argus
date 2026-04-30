package com.argus.rag.common.exception;

/**
 * 业务异常，抛出时由 {@link GlobalExceptionHandler} 统一处理，返回 HTTP 400。
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
