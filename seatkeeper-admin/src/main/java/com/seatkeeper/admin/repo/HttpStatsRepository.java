package com.seatkeeper.admin.repo;

import com.fasterxml.jackson.databind.JsonNode;
import com.seatkeeper.admin.api.ApiEnvelope;
import com.seatkeeper.admin.api.ApiException;
import com.seatkeeper.admin.domain.Stats;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * 真实接口：GET /api/v1/admin/stats?from&to
 *
 * data 直接是 StatsVO：{totalReservations, checkedIn, noShow, checkInRate, noShowRate}
 */
public final class HttpStatsRepository implements StatsRepository {

    private final String baseUrl;
    private final String token;
    private final HttpClient http;

    public HttpStatsRepository(String baseUrl, String token) {
        this.baseUrl = baseUrl;
        this.token = token;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    @Override
    public Stats load(OffsetDateTime from, OffsetDateTime to) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/admin/stats" + queryOf(from, to)))
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
        return new Stats(
                data.path("totalReservations").asLong(0),
                data.path("checkedIn").asLong(0),
                data.path("noShow").asLong(0),
                data.path("checkInRate").asDouble(0),
                data.path("noShowRate").asDouble(0));
    }

    /** 只拼有值的参数 */
    String queryOf(OffsetDateTime from, OffsetDateTime to) {
        StringBuilder sb = new StringBuilder();
        if (from != null) {
            append(sb, "from", from.toString());
        }
        if (to != null) {
            append(sb, "to", to.toString());
        }
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
}
