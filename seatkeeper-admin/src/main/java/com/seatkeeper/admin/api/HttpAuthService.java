package com.seatkeeper.admin.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatkeeper.admin.session.Session;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 真实接口：POST /api/v1/auth/login
 *
 * 请求体 {"studentNo":"...","password":"..."}
 * 响应   {"code":"OK","message":"操作成功","data":{"token":"...","role":"ADMIN","name":"...","credit":100,"restrictedUntil":null}}
 * 失败   {"code":"UNAUTHORIZED","message":"学号或密码错误","data":null}
 */
public final class HttpAuthService implements AuthService {

    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public HttpAuthService(String baseUrl) {
        this.baseUrl = baseUrl;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    @Override
    public CompletableFuture<LoginResult> login(String account, String password) {
        HttpRequest request;
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "studentNo", account == null ? "" : account,
                    "password", password == null ? "" : password));
            request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/auth/login"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
        } catch (Exception e) {
            return CompletableFuture.completedFuture(LoginResult.fail("请求构造失败：" + e.getMessage()));
        }

        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(this::toResult)
                .exceptionally(t -> LoginResult.fail("连不上服务端 " + baseUrl + "（" + rootMessage(t) + "）"));
    }

    private LoginResult toResult(HttpResponse<String> response) {
        ApiEnvelope envelope;
        try {
            envelope = ApiEnvelope.parse(response.body());
        } catch (ApiException e) {
            return LoginResult.fail(e.getMessage());
        }

        if (!envelope.isOk()) {
            return LoginResult.fail(envelope.friendlyMessage());
        }

        JsonNode data = envelope.data();
        String role = data == null ? "" : data.path("role").asText("");
        if (!"ADMIN".equalsIgnoreCase(role)) {
            // 接口本身可能返回学生令牌，这里再拦一次，别指望前端藏按钮
            return LoginResult.fail("该账号不是管理员，无法登录管理端");
        }

        return LoginResult.ok(new Session(
                data.path("token").asText(""),
                role,
                data.path("name").asText(""),
                data.path("credit").asInt(0),
                parseInstant(data.path("restrictedUntil"))));
    }

    /** restrictedUntil 是 ISO-8601 带偏移的字符串，可能是 null */
    private static Instant parseInstant(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String text = node.asText("");
        if (text.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (Exception e) {
            return null;
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null) {
            cur = cur.getCause();
        }
        return cur.getClass().getSimpleName() + ": " + cur.getMessage();
    }
}
