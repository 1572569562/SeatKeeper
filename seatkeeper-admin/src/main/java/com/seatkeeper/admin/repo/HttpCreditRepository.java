package com.seatkeeper.admin.repo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatkeeper.admin.api.ApiEnvelope;
import com.seatkeeper.admin.api.ApiException;
import com.seatkeeper.admin.domain.Credit;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 真实接口：查信用 / 调整信用。
 *
 * 注意 409 IDEMPOTENCY_CONFLICT：同一条预约 + 同一个原因只能调一次，
 * 要再调就换个原因。
 */
public final class HttpCreditRepository implements CreditRepository {

    private final String baseUrl;
    private final String token;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public HttpCreditRepository(String baseUrl, String token) {
        this.baseUrl = baseUrl;
        this.token = token;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    @Override
    public Credit get(long userId) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/admin/credit/" + userId))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .GET()
                .build();
        return toCredit(ApiEnvelope.parse(send(request).body()).requireData());
    }

    @Override
    public Credit adjust(long userId, long reservationId, int delta, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reservationId", reservationId);
        payload.put("delta", delta);
        payload.put("reason", reason == null ? "" : reason);

        String body;
        try {
            body = mapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new ApiException("BAD_REQUEST", "请求构造失败：" + e.getMessage(), e);
        }

        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(baseUrl + "/api/v1/admin/credit/" + userId + "/adjust"))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return toCredit(ApiEnvelope.parse(send(request).body()).requireData());
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ApiException("NETWORK", "连不上服务端 " + baseUrl, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException("NETWORK", "请求被中断", e);
        }
    }

    private static Credit toCredit(JsonNode node) {
        return new Credit(
                node.path("userId").asLong(),
                node.path("score").asInt(0),
                toTime(node.path("restrictedUntil")),
                node.path("version").asInt(0));
    }

    private static OffsetDateTime toTime(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String text = node.asText("");
        if (text.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text);
        } catch (Exception e) {
            return null;
        }
    }
}
