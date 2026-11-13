package cn.edu.stu.seatkeeper.model.po;

/**
 * 登录校验专用的用户凭证快照，包含密码哈希。
 *
 * <p>只在认证服务内部流转，禁止直接作为接口返回值。
 */
public record UserCredentialPO(long id, String studentNo, String name, String passwordHash,
                               String role, String status) { }
