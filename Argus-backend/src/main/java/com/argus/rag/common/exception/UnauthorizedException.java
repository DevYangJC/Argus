package com.argus.rag.common.exception;

/**
 * 未认证异常，由 {@link GlobalExceptionHandler} 统一处理，返回 HTTP 401。
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
