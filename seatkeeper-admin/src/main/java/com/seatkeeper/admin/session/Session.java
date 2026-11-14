package com.seatkeeper.admin.session;

import java.time.Instant;

/**
 * 登录成功后手里的东西。
 *
 * @param token            JWT，后续接口放 Authorization: Bearer &lt;token&gt;
 * @param role             角色，ADMIN 才能进管理端
 * @param name             姓名
 * @param credit           当前信用分
 * @param restrictedUntil  信用限制截止时间，可为 null（契约里是 LoginVO.restrictedUntil）
 */
public record Session(String token, String role, String name, int credit, Instant restrictedUntil) {

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }

    /** 是否还在信用惩罚期 */
    public boolean isRestricted() {
        return restrictedUntil != null && restrictedUntil.isAfter(Instant.now());
    }
}
