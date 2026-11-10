package cn.edu.stu.seatkeeper.exception;

import java.util.Objects;
/** 业务异常携带稳定错误码，提示文本由错误码统一管理。 */
public class BusinessException extends RuntimeException {
    private final CommonErrorCode errorCode;
    /** @param errorCode 通用业务错误码 */
    public BusinessException(CommonErrorCode errorCode) {
        super(Objects.requireNonNull(errorCode, "错误码不能为空").getMessage());
        this.errorCode = errorCode;
    }
    /** @return 业务错误码 */
    public CommonErrorCode getErrorCode() { return errorCode; }
}
