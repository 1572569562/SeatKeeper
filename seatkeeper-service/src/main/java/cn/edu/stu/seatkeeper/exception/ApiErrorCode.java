package cn.edu.stu.seatkeeper.exception;

import org.springframework.http.HttpStatus;

/**
 * 统一错误码契约：HTTP 状态、对外稳定编码与安全提示。
 *
 * <p>实现者必须是枚举，默认以枚举名作为对外 {@code code}，这样契约文档与代码天然一致。
 */
public interface ApiErrorCode {

    /**
     * @return 对应的 HTTP 响应状态
     */
    HttpStatus getStatus();

    /**
     * @return 面向调用者的提示，不得包含内部细节
     */
    String getMessage();

    /**
     * @return 对外稳定错误码，默认取枚举名
     */
    default String code() {
        return this instanceof Enum<?> value ? value.name() : String.valueOf(this);
    }
}
