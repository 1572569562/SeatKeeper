package com.seatkeeper.admin.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 响应信封解析。这里的 JSON 全部抄自 contracts/openapi.yaml 的示例。
 */
class ApiEnvelopeTest {

    @Test
    void parsesSuccessEnvelope() {
        ApiEnvelope envelope = ApiEnvelope.parse(
                "{\"code\":\"OK\",\"message\":\"操作成功\",\"data\":{\"token\":\"eyJ...\",\"role\":\"ADMIN\"}}");

        assertTrue(envelope.isOk());
        assertEquals("ADMIN", envelope.requireData().path("role").asText());
    }

    @Test
    void parsesErrorEnvelope() {
        ApiEnvelope envelope = ApiEnvelope.parse(
                "{\"code\":\"SEAT_DISABLED\",\"message\":\"该座位已停用\",\"data\":null}");

        assertFalse(envelope.isOk());
        assertEquals("SEAT_DISABLED", envelope.code());
        assertEquals("该座位已停用", envelope.friendlyMessage());
    }

    @Test
    void translatesKnownCodesToChinese() {
        assertEquals("登录已过期，请重新登录",
                ApiEnvelope.parse("{\"code\":\"TOKEN_INVALID\",\"message\":\"x\"}").friendlyMessage());
        assertEquals("当前账号没有权限执行这个操作",
                ApiEnvelope.parse("{\"code\":\"FORBIDDEN\",\"message\":\"x\"}").friendlyMessage());
        assertEquals("学号或密码错误",
                ApiEnvelope.parse("{\"code\":\"UNAUTHORIZED\",\"message\":\"x\"}").friendlyMessage());
    }

    @Test
    void unknownCodeKeepsServerMessage() {
        assertEquals("服务器开小差",
                ApiEnvelope.parse("{\"code\":\"SOMETHING_NEW\",\"message\":\"服务器开小差\"}").friendlyMessage());
    }

    @Test
    void requireDataThrowsOnError() {
        ApiEnvelope envelope = ApiEnvelope.parse("{\"code\":\"FORBIDDEN\",\"message\":\"没权限\",\"data\":null}");
        ApiException error = assertThrows(ApiException.class, envelope::requireData);
        assertEquals("FORBIDDEN", error.getCode());
    }

    @Test
    void garbageBodyIsRejected() {
        ApiException error = assertThrows(ApiException.class, () -> ApiEnvelope.parse("<html>502</html>"));
        assertEquals("BAD_RESPONSE", error.getCode());
    }
}
