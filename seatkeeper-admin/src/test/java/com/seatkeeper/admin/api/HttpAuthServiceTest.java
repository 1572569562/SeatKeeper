package com.seatkeeper.admin.api;

import com.seatkeeper.admin.authsupport.FakeSeatServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证登录接口的响应信封解析与角色拦截。 */
class HttpAuthServiceTest {

    private FakeSeatServer server;
    private HttpAuthService service;

    @BeforeEach
    void setUp() throws Exception {
        server = new FakeSeatServer();
        service = new HttpAuthService(server.baseUrl());
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private LoginResult login() throws Exception {
        return service.login("admin", "admin123").get(10, TimeUnit.SECONDS);
    }

    @Test
    void adminLoginSucceeds() throws Exception {
        LoginResult result = login();
        assertTrue(result.success(), result.message());
        assertEquals("test-token", result.session().token());
        assertEquals("ADMIN", result.session().role());
        assertEquals("管理员·测试", result.session().name());
        assertEquals(95, result.session().credit());
        assertFalse(result.session().isRestricted());
    }

    @Test
    void studentTokenIsRejectedByClient() throws Exception {
        server.loginRole = "STUDENT";
        LoginResult result = login();
        assertFalse(result.success());
        assertTrue(result.message().contains("不是管理员"), result.message());
    }

    @Test
    void unauthorizedBecomesChineseMessage() throws Exception {
        server.forcedErrorJson = "{\"code\":\"UNAUTHORIZED\",\"message\":\"学号或密码错误\",\"data\":null}";
        LoginResult result = login();
        assertFalse(result.success());
        assertEquals("学号或密码错误", result.message());
    }

    @Test
    void unreachableServerGivesReadableError() {
        HttpAuthService broken = new HttpAuthService("http://127.0.0.1:1");
        LoginResult result = broken.login("admin", "x").join();
        assertFalse(result.success());
        assertTrue(result.message().contains("连不上服务端"), result.message());
    }
}
