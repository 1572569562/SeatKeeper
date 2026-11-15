package com.seatkeeper.admin.api;

/**
 * 调用服务端失败。code 是契约里那些稳定错误码（FORBIDDEN、SEAT_DISABLED…），
 * getMessage() 已经是给用户看的中文。
 */
public class ApiException extends RuntimeException {

    private final String code;

    public ApiException(String code, String message) {
        super(message);
        this.code = code;
    }

    public ApiException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
