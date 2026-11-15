package com.seatkeeper.admin.api;

import java.util.concurrent.CompletableFuture;

/**
 * 登录接口。界面只依赖这个接口，
 * 本地模拟和真实 HTTP 两种实现可以随时换，不改界面代码。
 */
public interface AuthService {

    CompletableFuture<LoginResult> login(String account, String password);
}
