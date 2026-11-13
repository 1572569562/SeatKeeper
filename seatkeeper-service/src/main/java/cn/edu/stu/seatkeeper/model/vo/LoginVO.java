package cn.edu.stu.seatkeeper.model.vo;

import java.time.OffsetDateTime;

/**
 * 登录成功响应，字段与 contracts/openapi.yaml 的 LoginVO 一致。
 *
 * @param token JWT
 * @param role 角色
 * @param name 姓名
 * @param credit 当前信用分
 * @param restrictedUntil 信用限制截止时间，可为空
 */
public record LoginVO(String token, String role, String name, int credit, OffsetDateTime restrictedUntil) { }
