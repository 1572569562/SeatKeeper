package com.seatkeeper.admin.repo;

import com.seatkeeper.admin.api.ApiException;
import com.seatkeeper.admin.domain.AdminReservation;
import com.seatkeeper.admin.domain.PageResult;
import com.seatkeeper.admin.domain.ReservationFilter;
import com.seatkeeper.admin.domain.ReservationStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 假数据的过滤、分页和 W5 的强制取消，行为要和真接口一致。 */
class MockReservationRepositoryTest {

    private static final ZoneOffset ZONE = ZoneOffset.ofHours(8);

    private final MockReservationRepository repository = new MockReservationRepository();

    private ReservationFilter filterOf(String studentNo, ReservationStatus status, int page, int size) {
        return new ReservationFilter(studentNo, null, status, null, null, page, size);
    }

    @Test
    void hasData() {
        List<AdminReservation> all = repository.all();
        assertFalse(all.isEmpty());
        assertTrue(all.size() > 20, "数据太少，演示不够：" + all.size());
    }

    @Test
    void everyReservationHasUserId() {
        repository.all().forEach(r -> assertTrue(r.userId() != null, "预约 " + r.id() + " 没有 userId"));
    }

    @Test
    void firstPageHonoursSize() {
        PageResult<AdminReservation> page = repository.search(filterOf(null, null, 1, 10));
        assertEquals(10, page.items().size());
        assertEquals(1, page.page());
        assertTrue(page.hasMore());
    }

    @Test
    void lastPageHasNoMore() {
        int total = repository.all().size();
        int size = 20;
        int lastPage = (total + size - 1) / size;

        PageResult<AdminReservation> page = repository.search(filterOf(null, null, lastPage, size));
        assertFalse(page.hasMore(), "最后一页不该说还有下一页");
        assertFalse(page.items().isEmpty());
    }

    @Test
    void filtersByStudentNoPartially() {
        PageResult<AdminReservation> page = repository.search(filterOf("20230101", null, 1, 100));
        assertFalse(page.isEmpty());
        page.items().forEach(r -> assertTrue(r.studentNo().contains("20230101")));
    }

    @Test
    void filtersByStatus() {
        PageResult<AdminReservation> page = repository.search(filterOf(null, ReservationStatus.EXPIRED, 1, 100));
        page.items().forEach(r -> assertEquals(ReservationStatus.EXPIRED, r.status()));
    }

    @Test
    void filtersByDateRange() {
        OffsetDateTime from = LocalDate.of(2026, 11, 14).atStartOfDay().atOffset(ZONE);
        OffsetDateTime to = LocalDate.of(2026, 11, 15).atStartOfDay().atOffset(ZONE);
        ReservationFilter filter = new ReservationFilter(null, null, null, from, to, 1, 100);

        PageResult<AdminReservation> page = repository.search(filter);
        assertFalse(page.isEmpty());
        page.items().forEach(r -> assertTrue(!r.slotStart().isBefore(from) && r.slotStart().isBefore(to)));
    }

    @Test
    void unknownStudentReturnsEmpty() {
        PageResult<AdminReservation> page = repository.search(filterOf("99999999", null, 1, 20));
        assertTrue(page.isEmpty());
        assertFalse(page.hasMore());
    }

    @Test
    void resultsSortedBySlotStartDescending() {
        List<AdminReservation> items = repository.search(filterOf(null, null, 1, 50)).items();
        for (int i = 1; i < items.size(); i++) {
            assertFalse(items.get(i).slotStart().isAfter(items.get(i - 1).slotStart()),
                    "第 " + i + " 条比前一条更新，排序不对");
        }
    }

    // ---------------- W5：强制取消 ----------------

    @Test
    void forceCancelMarksReservationCancelled() {
        AdminReservation target = repository.all().stream()
                .filter(AdminReservation::cancellable)
                .findFirst()
                .orElseThrow();

        AdminReservation cancelled = repository.forceCancel(target.id(), "座位设备故障");

        assertEquals(ReservationStatus.CANCELLED, cancelled.status());
        assertEquals(target.version() + 1, cancelled.version());
    }

    @Test
    void forceCancelRequiresReason() {
        AdminReservation target = repository.all().get(0);
        ApiException error = assertThrows(ApiException.class, () -> repository.forceCancel(target.id(), "  "));
        assertEquals("请填写取消原因", error.getMessage());
    }

    @Test
    void forceCancelRejectsFinishedReservation() {
        AdminReservation finished = repository.all().stream()
                .filter(r -> !r.cancellable())
                .findFirst()
                .orElseThrow();

        ApiException error = assertThrows(ApiException.class,
                () -> repository.forceCancel(finished.id(), "测试"));
        assertEquals("INVALID_STATE", error.getCode());
        assertTrue(error.getMessage().contains("不允许取消"), error.getMessage());
    }

    @Test
    void forceCancelUnknownId() {
        ApiException error = assertThrows(ApiException.class, () -> repository.forceCancel(1, "测试"));
        assertEquals("NOT_FOUND", error.getCode());
    }
}
