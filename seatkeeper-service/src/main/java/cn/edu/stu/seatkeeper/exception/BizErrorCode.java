package cn.edu.stu.seatkeeper.exception;

import org.springframework.http.HttpStatus;

/**
 * 业务错误码，与 contracts/openapi.yaml 的 ErrorResponse.code 一一对应。
 *
 * <p>对外只暴露这里的稳定编码，禁止把持久层的 {@code DataOperationException.Code}
 * 或数据库异常类名直接返回给调用端。
 */
public enum BizErrorCode implements ApiErrorCode {

    /** 学号或密码错误；不区分账号不存在与密码错误，避免枚举账号。 */
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "学号或密码错误"),
    /** 令牌缺失、过期或签名无效。 */
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "登录已失效，请重新登录"),
    /** 角色不足，例如学生令牌调用管理接口。 */
    FORBIDDEN(HttpStatus.FORBIDDEN, "没有权限执行该操作"),
    /** 资源不存在或不属于当前用户；后一种情况也返回 404，避免泄露他人数据。 */
    NOT_FOUND(HttpStatus.NOT_FOUND, "请求的资源不存在"),
    /** 同一座位同一时段已有活动预约，由唯一索引裁决。 */
    SEAT_TAKEN(HttpStatus.CONFLICT, "该座位已被预约"),
    /** 座位被管理员停用，禁止新预约。 */
    SEAT_DISABLED(HttpStatus.CONFLICT, "该座位已停用"),
    /** 预约当前状态不允许该操作，例如已取消后签到。 */
    INVALID_STATE(HttpStatus.CONFLICT, "当前状态不允许该操作"),
    /** 乐观锁版本不匹配，调用方应重新读取后再决策。 */
    VERSION_CONFLICT(HttpStatus.CONFLICT, "数据已被其他操作修改，请重试"),
    /** 同一幂等键的重放参数不一致。 */
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "重复请求的参数与首次不一致"),
    /** 同时预约数量超过业务上限。 */
    LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "最多同时预约 2 个座位"),
    /** 签到座位码与预约座位不一致。 */
    SEAT_CODE_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY, "座位码与预约座位不一致"),
    /** 时段不存在或已不可预约（已开始、已过期）。 */
    SLOT_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "该时段不存在或已不可预约"),
    /** 信用处于限制期内。 */
    CREDIT_RESTRICTED(HttpStatus.UNPROCESSABLE_ENTITY, "信用受限期间不能预约"),
    /** 信用变动后余额超出 0..100，数据层拒绝且不夹断。 */
    CREDIT_OUT_OF_RANGE(HttpStatus.UNPROCESSABLE_ENTITY, "信用余额超出允许范围"),
    /** 当前时间不在签到窗口内。 */
    CHECKIN_WINDOW(HttpStatus.UNPROCESSABLE_ENTITY, "不在签到时间窗口内"),
    /** 请求参数不合法。 */
    INVALID_ARGUMENT(HttpStatus.BAD_REQUEST, "请求参数不合法"),
    /** 未预期的服务端异常。 */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "服务暂时无法处理请求"),
    /** 数据库连接或访问失败。 */
    DATABASE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "数据库暂不可用");

    private final HttpStatus status;
    private final String message;

    /**
     * @param status 对应 HTTP 状态
     * @param message 安全提示
     */
    BizErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    @Override
    public HttpStatus getStatus() { return status; }

    @Override
    public String getMessage() { return message; }
}
