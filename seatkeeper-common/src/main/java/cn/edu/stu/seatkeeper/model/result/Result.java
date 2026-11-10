package cn.edu.stu.seatkeeper.model.result;

/** 统一响应模型；保留团队约定的字符串状态码和字段名称。 */
public record Result<T>(String code, String message, T data) { }
