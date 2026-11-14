package com.seatkeeper.admin.api;

import com.seatkeeper.admin.session.Session;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 本地模拟登录：服务端还没起来的时候用，不依赖网络。
 * 明确写了“模拟”两个字，别当成真接口。
 *
 * 演示账号：
 *   admin / admin123      -> 管理员，能进
 *   20230101 / 123456     -> 学生，必须被拦住（对应验收第 6 条和 W7 的“越权被拦住”）
 */
public final class MockAuthService implements AuthService {

    private record Account(String password, String role, String name, int credit) {
    }

    private static final Map<String, Account> ACCOUNTS = Map.of(
            "admin", new Account("admin123", "ADMIN", "管理员·演示", 100),
            "20230101", new Account("123456", "STUDENT", "张某", 80),
            "20230102", new Account("123456", "STUDENT", "李某", 65)
    );

    private final long delayMillis;

    public MockAuthService() {
        this(350);
    }

    /** 测试里传 0，省得等。 */
    public MockAuthService(long delayMillis) {
        this.delayMillis = delayMillis;
    }

    @Override
    public CompletableFuture<LoginResult> login(String account, String password) {
        return CompletableFuture.supplyAsync(() -> {
            sleepQuietly(delayMillis);

            String key = account == null ? "" : account.trim().toLowerCase(Locale.ROOT);
            Account acc = ACCOUNTS.get(key);

            if (acc == null || !acc.password().equals(password)) {
                return LoginResult.fail("账号或密码错误");
            }
            if (!"ADMIN".equals(acc.role())) {
                return LoginResult.fail("该账号不是管理员，无法登录管理端");
            }
            return LoginResult.ok(new Session("mock-token-" + key, acc.role(), acc.name(), acc.credit(), null));
        });
    }

    private static void sleepQuietly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
