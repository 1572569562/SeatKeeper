package com.seatkeeper.admin.repo;

import com.seatkeeper.admin.api.ApiException;
import com.seatkeeper.admin.authsupport.FakeSeatServer;
import com.seatkeeper.admin.domain.Stats;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 统计接口的参数拼接与解析。 */
class HttpStatsRepositoryTest {

    private static final ZoneOffset ZONE = ZoneOffset.ofHours(8);

    private FakeSeatServer server;
    private HttpStatsRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        server = new FakeSeatServer();
        repository = new HttpStatsRepository(server.baseUrl(), "test-token");
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void parsesStats() {
        Stats stats = repository.load(null, null);

        assertEquals(52, stats.totalReservations());
        assertEquals(23, stats.checkedIn());
        assertEquals(12, stats.noShow());
        assertEquals(0.4423, stats.checkInRate(), 1e-3);
        assertEquals("44.2%", stats.checkInPercent());
        assertEquals("23.1%", stats.noShowPercent());
        assertFalse(stats.isEmpty());
    }

    @Test
    void sendsNoQueryWhenDatesAreEmpty() {
        repository.load(null, null);
        String request = server.requests().get(0);
        assertTrue(request.startsWith("GET /api/v1/admin/stats "), request);
        assertTrue(repository.queryOf(null, null).isEmpty());
    }

    @Test
    void sendsBothDates() {
        OffsetDateTime from = LocalDate.of(2026, 11, 14).atStartOfDay().atOffset(ZONE);
        OffsetDateTime to = LocalDate.of(2026, 11, 15).atStartOfDay().atOffset(ZONE);

        repository.load(from, to);

        String query = server.queries().get(0);
        assertTrue(query.contains("from=2026-11-14T00:00+08:00"), query);
        assertTrue(query.contains("to=2026-11-15T00:00+08:00"), query);
    }

    @Test
    void forbiddenEnvelopeBecomesReadableError() {
        server.forcedErrorJson = "{\"code\":\"FORBIDDEN\",\"message\":\"没有权限\",\"data\":null}";
        ApiException error = assertThrows(ApiException.class, () -> repository.load(null, null));
        assertEquals("当前账号没有权限执行这个操作", error.getMessage());
    }
}
