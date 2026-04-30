package com.argus.rag.common.exception;

/**
 * 权限不足异常，由 {@link GlobalExceptionHandler} 统一处理，返回 HTTP 403。
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
