package cn.edu.stu.seatkeeper.exception;

/** 可交由成员3映射为业务错误的持久层异常；默认触发事务回滚。 */
public final class DataOperationException extends RuntimeException {
    public enum Code { SLOT_NOT_FOUND, RESERVATION_CONFLICT, NOT_FOUND, OWNERSHIP_MISMATCH,
        IDEMPOTENCY_MISMATCH, CREDIT_OUT_OF_RANGE, VERSION_CONFLICT }

    private final Code code;

    /**
     * 创建携带持久化错误码的异常，必要时保留底层异常原因以供排查。
     * @param code 持久化领域错误码
     * @param message 可供上层判断的错误说明
     */
    public DataOperationException(Code code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 创建携带持久化错误码的异常，必要时保留底层异常原因以供排查。
     * @param code 持久化领域错误码
     * @param message 可供上层判断的错误说明
     * @param cause 原始异常原因
     */
    public DataOperationException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /**
     * 返回持久化领域错误码，供上层接口区分冲突、归属和版本等失败原因。
     */
    public Code code() { return code; }
}
