package cn.edu.stu.seatkeeper.model.result;

/** 无状态响应构造工具。 */
public final class ResultUtil {
    /**
     * 禁止实例化无状态工具类，只通过静态方法调用。
     */
    private ResultUtil() { }
    /**
     * 构造成功响应。
     * @param data 返回数据
     * @param <T> 数据类型
     * @return 团队约定的成功响应
     */
    public static <T> Result<T> success(T data) { return new Result<>("OK", "操作成功", data); }
    /**
     * 构造失败响应，不携带内部异常或数据库信息。
     * @param code 稳定的错误码
     * @param message 面向调用者的说明
     * @param <T> 数据类型
     * @return 失败响应
     */
    public static <T> Result<T> failure(String code, String message) { return new Result<>(code, message, null); }
}
