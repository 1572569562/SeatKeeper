package cn.edu.stu.seatkeeper.model.vo;

/** 健康接口视图对象，不暴露连接配置或持久化模型。 */
public record HealthVO(String status, String database) { }
