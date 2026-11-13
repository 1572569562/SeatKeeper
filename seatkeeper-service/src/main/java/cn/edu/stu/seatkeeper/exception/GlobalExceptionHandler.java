package cn.edu.stu.seatkeeper.exception;

import cn.edu.stu.seatkeeper.model.result.Result;
import cn.edu.stu.seatkeeper.model.result.ResultUtil;
import jakarta.validation.ConstraintViolationException;
import org.springframework.beans.TypeMismatchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 将异常转换为稳定响应，避免返回连接串、原始输入或堆栈。 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * @param error 数据访问异常
     * @return 不含连接信息的不可用响应
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Result<Void>> databaseUnavailable(DataAccessException error) {
        LOGGER.warn("数据库请求失败，异常类型：{}", error.getClass().getSimpleName());
        return response(BizErrorCode.DATABASE_UNAVAILABLE);
    }

    /**
     * @param error 参数校验异常
     * @return 安全的参数错误响应
     */
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class,
        ConstraintViolationException.class, HttpMessageNotReadableException.class, TypeMismatchException.class})
    public ResponseEntity<Result<Void>> invalidArgument(Exception error) {
        LOGGER.warn("请求参数不合法，异常类型：{}", error.getClass().getSimpleName());
        return response(BizErrorCode.INVALID_ARGUMENT);
    }

    /**
     * @param error 业务异常
     * @return 业务错误码对应的响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> businessError(BusinessException error) {
        return response(error.getErrorCode());
    }

    /**
     * 把持久层错误码翻译成对外业务错误码，禁止直接暴露 RESERVATION_CONFLICT 这类内部编码。
     * @param error 数据操作冲突
     * @return 稳定的领域错误码
     */
    @ExceptionHandler(DataOperationException.class)
    public ResponseEntity<Result<Void>> dataOperation(DataOperationException error) {
        return response(translate(error.code()));
    }

    /**
     * @param error 未处理异常
     * @return 不含内部细节的服务器错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> unexpectedError(Exception error) {
        // 保留框架协议异常的状态和响应头，例如不存在的路由与不支持的请求方法。
        if (error instanceof ErrorResponse response) {
            return ResponseEntity.status(response.getStatusCode()).headers(response.getHeaders())
                .body(ResultUtil.failure("HTTP_" + response.getStatusCode().value(), "请求无法处理"));
        }
        LOGGER.error("请求处理异常，异常类型：{}", error.getClass().getSimpleName());
        return response(BizErrorCode.INTERNAL_ERROR);
    }

    /**
     * 持久层错误码到业务错误码的映射；归属不符刻意映射为 404，避免泄露他人预约是否存在。
     * @param code 持久层错误码
     * @return 对外业务错误码
     */
    static BizErrorCode translate(DataOperationException.Code code) {
        return switch (code) {
            case SLOT_NOT_FOUND -> BizErrorCode.SLOT_INVALID;
            case RESERVATION_CONFLICT -> BizErrorCode.SEAT_TAKEN;
            case NOT_FOUND, OWNERSHIP_MISMATCH -> BizErrorCode.NOT_FOUND;
            case IDEMPOTENCY_MISMATCH -> BizErrorCode.IDEMPOTENCY_CONFLICT;
            case CREDIT_OUT_OF_RANGE -> BizErrorCode.CREDIT_OUT_OF_RANGE;
            case VERSION_CONFLICT -> BizErrorCode.VERSION_CONFLICT;
        };
    }

    /**
     * 按统一错误码构造响应状态和安全提示，不返回内部异常细节。
     * @param code 错误码
     */
    private ResponseEntity<Result<Void>> response(ApiErrorCode code) {
        return ResponseEntity.status(code.getStatus()).body(ResultUtil.failure(code.code(), code.getMessage()));
    }
}
