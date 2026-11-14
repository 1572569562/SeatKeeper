package com.seatkeeper.admin.repo;

import com.seatkeeper.admin.api.ApiException;
import com.seatkeeper.admin.authsupport.FakeSeatServer;
import com.seatkeeper.admin.domain.AdminReservation;
import com.seatkeeper.admin.domain.PageResult;
import com.seatkeeper.admin.domain.ReservationFilter;
import com.seatkeeper.admin.domain.ReservationStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证预约查询的参数拼接、分页解析和时间解析。 */
class HttpReservationRepositoryTest {

    private static final ZoneOffset ZONE = ZoneOffset.ofHours(8);

    private FakeSeatServer server;
    private HttpReservationRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        server = new FakeSeatServer();
        repository = new HttpReservationRepository(server.baseUrl(), "test-token");
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void parsesPageResult() {
        PageResult<AdminReservation> page = repository.search(ReservationFilter.firstPage(20));

        assertEquals(2, page.items().size());
        assertEquals(2, page.page());
        assertEquals(2, page.size());
        assertTrue(page.hasMore());
    }

    @Test
    void parsesReservationFields() {
        AdminReservation first = repository.search(ReservationFilter.firstPage(20)).items().get(0);

        assertEquals(8801, first.id());
        assertEquals("20230101", first.studentNo());
        assertEquals("张某", first.studentName());
        assertEquals("A-027", first.seatNo());
        assertEquals(1, first.floor());
        assertEquals("1楼 A区 A-027", first.seatText());
        assertEquals("11-14 08:00 ~ 10:00", first.slotText());
        assertEquals(ReservationStatus.CHECKED_IN, first.status());
        assertEquals("11-14 08:03", first.checkedInText());
        assertEquals(OffsetDateTime.of(2026, 11, 14, 8, 0, 0, 0, ZONE), first.slotStart());
    }

    @Test
    void nullCheckedInAtBecomesDash() {
        AdminReservation second = repository.search(ReservationFilter.firstPage(20)).items().get(1);
        assertNull(second.checkedInAt());
        assertEquals("—", second.checkedInText());
        assertEquals(ReservationStatus.RESERVED, second.status());
    }

    @Test
    void sendsOnlyProvidedFilters() {
        ReservationFilter filter = new ReservationFilter(
                "20230101", null, ReservationStatus.RESERVED,
                LocalDate.of(2026, 11, 14).atStartOfDay().atOffset(ZONE),
                LocalDate.of(2026, 11, 15).atStartOfDay().atOffset(ZONE),
                2, 10);

        repository.search(filter);

        // 服务端收到的已经是解码后的 query，所以这里断言的是解码形态
        String query = server.queries().get(0);
        assertTrue(query.contains("studentNo=20230101"), query);
        assertTrue(query.contains("status=RESERVED"), query);
        assertTrue(query.contains("from=2026-11-14T00:00+08:00"), query);
        assertTrue(query.contains("to=2026-11-15T00:00+08:00"), query);
        assertTrue(query.contains("page=2"), query);
        assertTrue(query.contains("size=10"), query);
        assertTrue(!query.contains("seatId"), "没填座位 ID 就不该带这个参数：" + query);
    }

    @Test
    void forbiddenEnvelopeBecomesReadableError() {
        server.forcedErrorJson = "{\"code\":\"FORBIDDEN\",\"message\":\"没有权限\",\"data\":null}";
        ApiException error = assertThrows(ApiException.class,
                () -> repository.search(ReservationFilter.firstPage(20)));
        assertEquals("当前账号没有权限执行这个操作", error.getMessage());
    }
}
