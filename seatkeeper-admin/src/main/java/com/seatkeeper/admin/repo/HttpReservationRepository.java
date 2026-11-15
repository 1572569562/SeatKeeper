package com.seatkeeper.admin.repo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatkeeper.admin.api.ApiEnvelope;
import com.seatkeeper.admin.api.ApiException;
import com.seatkeeper.admin.domain.AdminReservation;
import com.seatkeeper.admin.domain.PageResult;
import com.seatkeeper.admin.domain.ReservationFilter;
import com.seatkeeper.admin.domain.ReservationStatus;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 真实接口：GET /api/v1/admin/reservations?studentNo&seatId&status&from&to&page&size
 *          POST /api/v1/admin/reservations/{id}/force-cancel
 *
 * 响应信封里的 data 是 {items, page, size, hasMore}，契约没有 total。
 */
public final class HttpReservationRepository implements ReservationRepository {

    private final String baseUrl;
    private final String token;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public HttpReservationRepository(String baseUrl, String token) {
        this.baseUrl = baseUrl;
        this.token = token;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    @Override
    public PageResult<AdminReservation> search(ReservationFilter filter) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/admin/reservations" + queryOf(filter)))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ApiException("NETWORK", "连不上服务端 " + baseUrl, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException("NETWORK", "请求被中断", e);
        }

        JsonNode data = ApiEnvelope.parse(response.body()).requireData();
        List<AdminReservation> items = new ArrayList<>();
        JsonNode array = data.path("items");
        if (array.isArray()) {
            array.forEach(node -> items.add(toReservation(node)));
        }
        return new PageResult<>(items,
                data.path("page").asInt(filter.page()),
                data.path("size").asInt(filter.size()),
                data.path("hasMore").asBoolean(false));
    }

    /** 强制取消，原因必填；返回服务端给的「已取消」预约 */
    @Override
    public AdminReservation forceCancel(long reservationId, String reason) {
        String body;
        try {
            body = mapper.writeValueAsString(Map.of("reason", reason == null ? "" : reason));
        } catch (Exception e) {
            throw new ApiException("BAD_REQUEST", "请求构造失败：" + e.getMessage(), e);
        }

        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(baseUrl + "/api/v1/admin/reservations/" + reservationId + "/force-cancel"))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = send(request);
        return toReservation(ApiEnvelope.parse(response.body()).requireData());
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

    /** 只拼有值的参数，空条件不发给服务端 */
    String queryOf(ReservationFilter filter) {
        StringBuilder sb = new StringBuilder();
        append(sb, "studentNo", filter.studentNo());
        if (filter.seatId() != null) {
            append(sb, "seatId", String.valueOf(filter.seatId()));
        }
        if (filter.status() != null) {
            append(sb, "status", filter.status().code());
        }
        if (filter.from() != null) {
            append(sb, "from", filter.from().toString());
        }
        if (filter.to() != null) {
            append(sb, "to", filter.to().toString());
        }
        append(sb, "page", String.valueOf(Math.max(1, filter.page())));
        append(sb, "size", String.valueOf(Math.max(1, filter.size())));
        return sb.length() == 0 ? "" : "?" + sb;
    }

    private static void append(StringBuilder sb, String name, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append('&');
        }
        sb.append(name).append('=').append(URLEncoder.encode(value.trim(), StandardCharsets.UTF_8));
    }

    private static AdminReservation toReservation(JsonNode node) {
        return new AdminReservation(
                node.path("id").asLong(),
                // 契约目前没返回 userId，等成员3 补上；没有就留空，不编造
                node.hasNonNull("userId") ? node.get("userId").asLong() : null,
                node.path("studentNo").asText(""),
                node.path("studentName").asText(""),
                node.path("seatId").asLong(),
                node.path("seatNo").asText(""),
                node.path("room").asText(""),
                node.path("floor").asInt(1),
                toTime(node.path("slotStart")),
                toTime(node.path("slotEnd")),
                toTime(node.path("checkInDeadline")),
                toTime(node.path("checkedInAt")),
                ReservationStatus.ofCode(node.path("status").asText("")),
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
