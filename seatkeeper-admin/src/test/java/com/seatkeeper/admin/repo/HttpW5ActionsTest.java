package com.seatkeeper.admin.repo;

import com.seatkeeper.admin.api.ApiException;
import com.seatkeeper.admin.authsupport.FakeSeatServer;
import com.seatkeeper.admin.domain.AdminReservation;
import com.seatkeeper.admin.domain.Credit;
import com.seatkeeper.admin.domain.ReservationStatus;
import com.seatkeeper.admin.domain.ReservationFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** W5：强制取消 + 信用查询/调整，全部对着假服务端验。 */
class HttpW5ActionsTest {

    private FakeSeatServer server;
    private HttpReservationRepository reservations;
    private HttpCreditRepository credits;

    @BeforeEach
    void setUp() throws Exception {
        server = new FakeSeatServer();
        reservations = new HttpReservationRepository(server.baseUrl(), "test-token");
        credits = new HttpCreditRepository(server.baseUrl(), "test-token");
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    // ---------------- 强制取消 ----------------

    @Test
    void forceCancelPostsReasonAndReturnsUpdatedReservation() {
        AdminReservation cancelled = reservations.forceCancel(8802, "座位设备故障");

        assertEquals(ReservationStatus.CANCELLED, cancelled.status());
        assertEquals(1, cancelled.version());
        assertEquals(8802, cancelled.id());

        String request = server.requests().get(0);
        assertTrue(request.startsWith("POST /api/v1/admin/reservations/8802/force-cancel"), request);
        assertTrue(request.contains("Bearer test-token"), request);
        assertTrue(server.lastBody.contains("座位设备故障"), "请求体没带上原因：" + server.lastBody);
    }

    @Test
    void forceCancelInvalidStateBecomesReadableError() {
        server.forcedErrorJson = "{\"code\":\"INVALID_STATE\",\"message\":\"x\",\"data\":null}";
        ApiException error = assertThrows(ApiException.class,
                () -> reservations.forceCancel(8802, "测试"));
        assertEquals("当前状态不允许这个操作", error.getMessage());
    }

    @Test
    void forceCancelNotFound() {
        server.forcedErrorJson = "{\"code\":\"NOT_FOUND\",\"message\":\"x\",\"data\":null}";
        assertThrows(ApiException.class, () -> reservations.forceCancel(1, "测试"));
    }

    // ---------------- 信用 ----------------

    @Test
    void readsCredit() {
        Credit credit = credits.get(5001);
        assertEquals(5001, credit.userId());
        assertEquals(85, credit.score());
        assertNull(credit.restrictedUntil());
        assertEquals(2, credit.version());
        assertEquals("正常", credit.levelText());
        assertTrue(!credit.isRestricted());
    }

    @Test
    void adjustPostsReservationDeltaAndReason() {
        Credit credit = credits.adjust(5001, 8802, -10, "ADMIN_APPEAL");

        assertEquals(75, credit.score());
        assertNotNull(credit.restrictedUntil());
        assertEquals(3, credit.version());

        String request = server.requests().get(0);
        assertTrue(request.startsWith("POST /api/v1/admin/credit/5001/adjust"), request);

        String body = server.lastBody.replace(" ", "");
        assertTrue(body.contains("\"reservationId\":8802"), body);
        assertTrue(body.contains("\"delta\":-10"), body);
        assertTrue(body.contains("\"reason\":\"ADMIN_APPEAL\""), body);
    }

    @Test
    void adjustIdempotencyConflictIsTranslated() {
        server.forcedErrorJson = "{\"code\":\"IDEMPOTENCY_CONFLICT\",\"message\":\"重复请求的参数与首次不一致\",\"data\":null}";
        ApiException error = assertThrows(ApiException.class,
                () -> credits.adjust(5001, 8802, -10, "ADMIN_APPEAL"));
        assertEquals("重复请求的参数与首次不一致", error.getMessage());
    }

    @Test
    void reservationSearchStillWorksAlongsideActions() {
        assertTrue(!reservations.search(ReservationFilter.firstPage(20)).isEmpty());
    }
}
