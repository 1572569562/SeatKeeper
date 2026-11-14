package com.seatkeeper.admin.repo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatkeeper.admin.api.ApiEnvelope;
import com.seatkeeper.admin.api.ApiException;
import com.seatkeeper.admin.domain.Seat;
import com.seatkeeper.admin.domain.SeatStatus;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * 真实接口实现（W3 接的是接口，不是数据库）。
 *
 * <pre>
 * GET   /api/v1/admin/seats                 -> data: SeatVO[]
 * PATCH /api/v1/admin/seats/{id}  {"status":"DISABLED"} -> data: SeatVO
 * </pre>
 *
 * 座位是预置数据，接口没有「新增 / 删除座位」，管理端只能查和改状态。
 * 每次改状态都用服务端返回的座位覆盖本地缓存，保证界面和服务端一致。
 */
public final class HttpSeatRepository implements SeatRepository {

    private final String baseUrl;
    private final String token;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    /** 一次拉全量，按楼层/阅览室在前端过滤 */
    private List<Seat> cache = new ArrayList<>();

    public HttpSeatRepository(String baseUrl, String token) {
        this.baseUrl = baseUrl;
        this.token = token;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    @Override
    public List<Integer> floors() {
        ensureLoaded();
        TreeSet<Integer> result = new TreeSet<>();
        for (Seat seat : cache) {
            result.add(seat.floor());
        }
        return new ArrayList<>(result);
    }

    @Override
    public List<String> roomsOf(int floor) {
        ensureLoaded();
        List<String> result = new ArrayList<>();
        for (Seat seat : cache) {
            if (seat.floor() == floor && !result.contains(seat.room())) {
                result.add(seat.room());
            }
        }
        return result;
    }

    @Override
    public List<Seat> seatsOf(int floor, String room) {
        ensureLoaded();
        List<Seat> result = new ArrayList<>();
        for (Seat seat : cache) {
            if (seat.floor() == floor && seat.room().equals(room)) {
                result.add(seat);
            }
        }
        return result;
    }

    @Override
    public Optional<Seat> updateStatus(long seatId, SeatStatus newStatus) {
        String body;
        try {
            body = mapper.writeValueAsString(Map.of("status", newStatus.code()));
        } catch (Exception e) {
            throw new ApiException("BAD_REQUEST", "请求构造失败：" + e.getMessage(), e);
        }

        HttpResponse<String> response = send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/admin/seats/" + seatId))
                        .timeout(Duration.ofSeconds(10))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json; charset=UTF-8")
                        .method("PATCH", HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build());

        Seat updated = toSeat(ApiEnvelope.parse(response.body()).requireData());
        replaceInCache(updated);
        return Optional.of(updated);
    }

    @Override
    public void refresh() {
        cache = fetchSeats();
    }

    private void ensureLoaded() {
        if (cache.isEmpty()) {
            cache = fetchSeats();
        }
    }

    private List<Seat> fetchSeats() {
        HttpResponse<String> response = send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/admin/seats"))
                        .timeout(Duration.ofSeconds(10))
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "application/json")
                        .GET()
                        .build());

        JsonNode data = ApiEnvelope.parse(response.body()).requireData();
        if (!data.isArray()) {
            throw new ApiException("BAD_RESPONSE", "座位列表格式不对");
        }
        List<Seat> result = new ArrayList<>();
        data.forEach(node -> result.add(toSeat(node)));
        return result;
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

    private static Seat toSeat(JsonNode node) {
        return new Seat(
                node.path("id").asLong(),
                node.path("room").asText(""),
                node.path("floor").asInt(1),
                node.path("seatNo").asText(""),
                SeatStatus.ofCode(node.path("status").asText("AVAILABLE")));
    }

    private void replaceInCache(Seat updated) {
        for (int i = 0; i < cache.size(); i++) {
            if (cache.get(i).id() == updated.id()) {
                cache.set(i, updated);
                return;
            }
        }
        cache.add(updated);
    }
}
