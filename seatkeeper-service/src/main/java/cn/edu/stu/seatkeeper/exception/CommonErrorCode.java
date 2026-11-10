package cn.edu.stu.seatkeeper.exception;

import org.springframework.http.HttpStatus;
/** 通用错误码及对应的安全提示和响应状态。 */
public enum CommonErrorCode {
    INVALID_ARGUMENT("请求参数不合法", HttpStatus.BAD_REQUEST),
    DATABASE_UNAVAILABLE("数据库暂不可用", HttpStatus.SERVICE_UNAVAILABLE),
    INTERNAL_ERROR("服务暂时无法处理请求", HttpStatus.INTERNAL_SERVER_ERROR);
    private final String message;
    private final HttpStatus status;
    /**
     * 保存通用错误码对应的安全提示和HTTP响应状态。
     * @param message 可供上层判断的错误说明
     * @param status 对应的状态值
     */
    CommonErrorCode(String message, HttpStatus status) { this.message = message; this.status = status; }
    /** @return 可公开的错误提示 */
    public String getMessage() { return message; }
    /** @return 对应的响应状态 */
    public HttpStatus getStatus() { return status; }
}
