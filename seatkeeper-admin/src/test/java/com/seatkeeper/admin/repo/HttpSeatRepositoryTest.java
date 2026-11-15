package com.seatkeeper.admin.repo;

import com.seatkeeper.admin.api.ApiException;
import com.seatkeeper.admin.authsupport.FakeSeatServer;
import com.seatkeeper.admin.domain.Seat;
import com.seatkeeper.admin.domain.SeatStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 用假服务端验证 HttpSeatRepository 的解析、鉴权头和错误处理。 */
class HttpSeatRepositoryTest {

    private FakeSeatServer server;
    private HttpSeatRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        server = new FakeSeatServer();
        repository = new HttpSeatRepository(server.baseUrl(), "test-token");
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void readsFloorsAndRoomsFromSeatList() {
        assertEquals(List.of(1, 2), repository.floors());
        assertEquals(List.of("A区"), repository.roomsOf(1));
        assertEquals(List.of("C区"), repository.roomsOf(2));
    }

    @Test
    void filtersSeatsByFloorAndRoom() {
        List<Seat> seats = repository.seatsOf(1, "A区");
        assertEquals(2, seats.size());
        assertEquals("A-027", seats.get(0).seatNo());
        assertEquals(1, seats.get(0).floor());
    }

    @Test
    void mapsStatusString() {
        List<Seat> seats = repository.seatsOf(1, "A区");
        assertEquals(SeatStatus.AVAILABLE, seats.get(0).status());
        assertEquals(SeatStatus.DISABLED, seats.get(1).status());
    }

    @Test
    void sendsBearerToken() {
        repository.floors();
        assertTrue(server.requests().get(0).contains("auth=Bearer test-token"),
                "实际请求：" + server.requests().get(0));
    }

    @Test
    void patchUsesPatchMethodAndRefreshesCache() {
        repository.floors(); // 先加载

        Seat updated = repository.updateStatus(1027L, SeatStatus.DISABLED).orElseThrow();
        assertEquals(SeatStatus.DISABLED, updated.status());

        // 缓存被替换，再查就是停用
        assertEquals(SeatStatus.DISABLED, repository.seatsOf(1, "A区").get(0).status());

        boolean patchSent = server.requests().stream()
                .anyMatch(r -> r.startsWith("PATCH /api/v1/admin/seats/1027"));
        assertTrue(patchSent, "没有发出 PATCH 请求：" + server.requests());
    }

    @Test
    void forbiddenEnvelopeBecomesReadableError() {
        server.forcedErrorJson = "{\"code\":\"FORBIDDEN\",\"message\":\"没有权限\",\"data\":null}";
        ApiException error = assertThrows(ApiException.class, repository::floors);
        assertEquals("FORBIDDEN", error.getCode());
        assertEquals("当前账号没有权限执行这个操作", error.getMessage());
    }
}
