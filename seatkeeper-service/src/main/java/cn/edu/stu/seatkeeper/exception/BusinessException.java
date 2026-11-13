package cn.edu.stu.seatkeeper.exception;

import java.util.Objects;

/** 业务异常携带稳定错误码，提示文本由错误码统一管理。 */
public class BusinessException extends RuntimeException {
    private final ApiErrorCode errorCode;

    /**
     * @param errorCode 业务错误码，最终决定 HTTP 状态与对外 code
     */
    public BusinessException(ApiErrorCode errorCode) {
        super(Objects.requireNonNull(errorCode, "错误码不能为空").getMessage());
        this.errorCode = errorCode;
    }

    /**
     * 保留底层原因，便于日志排查；对外仍只暴露 errorCode 的提示。
     * @param errorCode 业务错误码
     * @param cause 原始异常
     */
    public BusinessException(ApiErrorCode errorCode, Throwable cause) {
        super(Objects.requireNonNull(errorCode, "错误码不能为空").getMessage(), cause);
        this.errorCode = errorCode;
    }

    /** @return 业务错误码 */
    public ApiErrorCode getErrorCode() { return errorCode; }
}
