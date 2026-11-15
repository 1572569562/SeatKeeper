package com.seatkeeper.admin.api;

import com.seatkeeper.admin.session.Session;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockAuthServiceTest {

    private final MockAuthService service = new MockAuthService(0);

    private LoginResult login(String account, String password) throws Exception {
        return service.login(account, password).get(5, TimeUnit.SECONDS);
    }

    @Test
    void adminCanLogin() throws Exception {
        LoginResult result = login("admin", "admin123");
        assertTrue(result.success());
        Session session = result.session();
        assertEquals("ADMIN", session.role());
        assertTrue(session.isAdmin());
    }

    @Test
    void studentIsRejectedByAdminClient() throws Exception {
        LoginResult result = login("20230101", "123456");
        assertFalse(result.success());
        assertTrue(result.message().contains("不是管理员"));
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        LoginResult result = login("admin", "wrong");
        assertFalse(result.success());
        assertEquals("账号或密码错误", result.message());
    }

    @Test
    void unknownAccountIsRejected() throws Exception {
        assertFalse(login("nobody", "whatever").success());
    }
}
