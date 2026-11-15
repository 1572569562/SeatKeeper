package com.seatkeeper.admin.authsupport;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 测试用的假服务端：按 contracts/openapi.yaml 的格式回 JSON。
 * 只为验证客户端的解析、鉴权头、错误码处理，不跑真实业务。
 */
public final class FakeSeatServer implements AutoCloseable {

    private final HttpServer server;
    private final List<String> requests = new ArrayList<>();

    /** 登录返回的 role，测试里可以改 */
    public String loginRole = "ADMIN";
    /** 座位列表返回的 JSON，测试里可以改 */
    public String seatsJson = """
            {"code":"OK","message":"操作成功","data":[
              {"id":1027,"room":"A区","floor":1,"seatNo":"A-027","status":"AVAILABLE"},
              {"id":1028,"room":"A区","floor":1,"seatNo":"A-028","status":"DISABLED"},
              {"id":1033,"room":"C区","floor":2,"seatNo":"C-001","status":"AVAILABLE"}
            ]}""";
    /** 座位状态更新返回的 JSON */
    public String patchJson = """
            {"code":"OK","message":"操作成功","data":
              {"id":1027,"room":"A区","floor":1,"seatNo":"A-027","status":"DISABLED"}}""";
    /** 预约查询返回的 JSON（契约的 AdminReservationPage） */
    public String reservationsJson = """
            {"code":"OK","message":"操作成功","data":{
              "items":[
                {"id":8801,"studentNo":"20230101","studentName":"张某","seatId":1027,
                 "seatNo":"A-027","room":"A区","floor":1,
                 "slotStart":"2026-11-14T08:00:00+08:00","slotEnd":"2026-11-14T10:00:00+08:00",
                 "checkInDeadline":"2026-11-14T08:15:00+08:00","checkedInAt":"2026-11-14T08:03:12+08:00",
                 "status":"CHECKED_IN","version":1},
                {"id":8802,"studentNo":"20230102","studentName":"李某","seatId":1028,
                 "seatNo":"A-028","room":"A区","floor":1,
                 "slotStart":"2026-11-14T10:00:00+08:00","slotEnd":"2026-11-14T12:00:00+08:00",
                 "checkInDeadline":"2026-11-14T10:15:00+08:00","checkedInAt":null,
                 "status":"RESERVED","version":0}
              ],
              "page":2,"size":2,"hasMore":true}}""";
    /** 非空时，所有请求都回这个错误信封（模拟 403 / 401） */
    public String forcedErrorJson;

    /** 强制取消返回的 JSON：状态变成 CANCELLED，版本 +1 */
    public String forceCancelJson = """
            {"code":"OK","message":"操作成功","data":
              {"id":8802,"studentNo":"20230102","studentName":"李某","seatId":1028,
               "seatNo":"A-028","room":"A区","floor":1,
               "slotStart":"2026-11-14T10:00:00+08:00","slotEnd":"2026-11-14T12:00:00+08:00",
               "checkInDeadline":"2026-11-14T10:15:00+08:00","checkedInAt":null,
               "status":"CANCELLED","version":1}}""";

    /** 查信用返回的 JSON */
    public String creditJson = """
            {"code":"OK","message":"操作成功","data":
              {"userId":5001,"score":85,"restrictedUntil":null,"version":2}}""";

    /** 调整信用返回的 JSON */
    public String creditAdjustJson = """
            {"code":"OK","message":"操作成功","data":
              {"userId":5001,"score":75,"restrictedUntil":"2026-11-21T20:00:00+08:00","version":3}}""";

    /** 统计看板返回的 JSON */
    public String statsJson = """
            {"code":"OK","message":"操作成功","data":
              {"totalReservations":52,"checkedIn":23,"noShow":12,
               "checkInRate":0.4423076923,"noShowRate":0.2307692308}}""";

    /** 最后一次收到的请求体，用来断言发出去的内容 */
    public String lastBody;

    private final List<String> queries = new ArrayList<>();

    public FakeSeatServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public List<String> queries() {
        return queries;
    }

    public List<String> requests() {
        return requests;
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        String query = exchange.getRequestURI().getQuery();
        queries.add(query);
        requests.add(method + " " + path + (query == null ? "" : "?" + query)
                + " | auth=" + exchange.getRequestHeaders().getFirst("Authorization"));

        // 记下请求体，便于断言发出去的内容
        byte[] requestBytes = exchange.getRequestBody().readAllBytes();
        if (requestBytes.length > 0) {
            lastBody = new String(requestBytes, StandardCharsets.UTF_8);
        }

        String body;
        int status = 200;
        if (forcedErrorJson != null) {
            body = forcedErrorJson;
            status = 403;
        } else if (path.endsWith("/api/v1/auth/login")) {
            body = "{\"code\":\"OK\",\"message\":\"操作成功\",\"data\":{"
                    + "\"token\":\"test-token\","
                    + "\"role\":\"" + loginRole + "\","
                    + "\"name\":\"管理员·测试\","
                    + "\"credit\":95,"
                    + "\"restrictedUntil\":null}}";
        } else if (path.endsWith("/force-cancel")) {
            body = forceCancelJson;
        } else if (path.endsWith("/api/v1/admin/reservations")) {
            body = reservationsJson;
        } else if (path.endsWith("/api/v1/admin/stats")) {
            body = statsJson;
        } else if (path.endsWith("/adjust")) {
            body = creditAdjustJson;
        } else if (path.matches(".*/api/v1/admin/credit/\\d+")) {
            body = creditJson;
        } else if (path.matches(".*/api/v1/admin/seats/\\d+")) {
            body = patchJson;
        } else {
            body = seatsJson;
        }

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
