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
        return response(CommonErrorCode.DATABASE_UNAVAILABLE);
    }
    /**
     * @param error 参数校验异常
     * @return 安全的参数错误响应
     */
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class,
        ConstraintViolationException.class, HttpMessageNotReadableException.class, TypeMismatchException.class})
    public ResponseEntity<Result<Void>> invalidArgument(Exception error) { return response(CommonErrorCode.INVALID_ARGUMENT); }
    /**
     * @param error 业务异常
     * @return 业务错误码对应的响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> businessError(BusinessException error) { return response(error.getErrorCode()); }
    /**
     * @param error 数据操作冲突
     * @return 稳定的领域错误码
     */
    @ExceptionHandler(DataOperationException.class)
    public ResponseEntity<Result<Void>> dataOperation(DataOperationException error) {
        return ResponseEntity.status(409).body(ResultUtil.failure(error.code().name(), "数据操作未满足约束"));
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
        return response(CommonErrorCode.INTERNAL_ERROR);
    }
    /**
     * 按统一错误码构造响应状态和安全提示，不返回内部异常细节。
     * @param code 持久化领域错误码
     */
    private ResponseEntity<Result<Void>> response(CommonErrorCode code) {
        return ResponseEntity.status(code.getStatus()).body(ResultUtil.failure(code.name(), code.getMessage()));
    }
}
