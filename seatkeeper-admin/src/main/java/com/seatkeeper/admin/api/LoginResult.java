package com.seatkeeper.admin.api;

import com.seatkeeper.admin.session.Session;

/** 一次登录的结果。message 直接拿来显示在界面上，不用再翻译。 */
public record LoginResult(boolean success, String message, Session session) {

    public static LoginResult ok(Session session) {
        return new LoginResult(true, "登录成功", session);
    }

    public static LoginResult fail(String message) {
        return new LoginResult(false, message, null);
    }
}
