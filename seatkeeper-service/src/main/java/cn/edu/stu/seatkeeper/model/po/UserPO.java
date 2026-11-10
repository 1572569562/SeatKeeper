package cn.edu.stu.seatkeeper.model.po;

/** 公开用户资料；密码字段不进入查询结果。 */
public record UserPO(long id, String studentNo, String name, String role, String status) { }
