package com.seatkeeper.admin.repo;

import com.seatkeeper.admin.domain.AdminReservation;
import com.seatkeeper.admin.domain.ReservationStatus;
import com.seatkeeper.admin.domain.Stats;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 统计要和假预约数据对得上，口径按契约。 */
class MockStatsRepositoryTest {

    private static final ZoneOffset ZONE = ZoneOffset.ofHours(8);

    private final MockReservationRepository reservations = new MockReservationRepository();
    private final MockStatsRepository stats = new MockStatsRepository(reservations);

    @Test
    void totalsMatchReservationData() {
        Stats result = stats.load(null, null);
        long total = reservations.all().size();

        assertEquals(total, result.totalReservations(), "总数应等于假数据条数");
    }

    @Test
    void checkedInCountsCheckedInAndCompleted() {
        Stats result = stats.load(null, null);
        long expected = reservations.all().stream()
                .filter(r -> r.status() == ReservationStatus.CHECKED_IN
                        || r.status() == ReservationStatus.COMPLETED)
                .count();
        assertEquals(expected, result.checkedIn());
    }

    @Test
    void noShowCountsExpired() {
        Stats result = stats.load(null, null);
        long expected = reservations.all().stream()
                .filter(r -> r.status() == ReservationStatus.EXPIRED)
                .count();
        assertEquals(expected, result.noShow());
    }

    @Test
    void ratesAreConsistentWithCounts() {
        Stats result = stats.load(null, null);

        assertEquals((double) result.checkedIn() / result.totalReservations(),
                result.checkInRate(), 1e-9);
        assertEquals((double) result.noShow() / result.totalReservations(),
                result.noShowRate(), 1e-9);
    }

    @Test
    void ratesStayWithinZeroAndOne() {
        Stats result = stats.load(null, null);
        assertTrue(result.checkInRate() >= 0 && result.checkInRate() <= 1);
        assertTrue(result.noShowRate() >= 0 && result.noShowRate() <= 1);
    }

    @Test
    void filtersByDateRange() {
        OffsetDateTime from = LocalDate.of(2026, 11, 14).atStartOfDay().atOffset(ZONE);
        Stats result = stats.load(from, null);

        long expected = reservations.all().stream()
                .filter(r -> !r.slotStart().isBefore(from))
                .count();
        assertEquals(expected, result.totalReservations());
    }

    @Test
    void emptyRangeGivesZeroes() {
        OffsetDateTime from = LocalDate.of(2030, 1, 1).atStartOfDay().atOffset(ZONE);
        Stats result = stats.load(from, null);

        assertEquals(Stats.empty(), result);
        assertTrue(result.isEmpty());
        assertEquals("0.0%", result.checkInPercent());
    }

    @Test
    void percentFormatting() {
        Stats result = stats.load(null, null);
        assertTrue(result.checkInPercent().endsWith("%"), result.checkInPercent());
        assertTrue(result.noShowPercent().endsWith("%"), result.noShowPercent());
    }

    @Test
    void everyReservationHasSlotStart() {
        reservations.all().forEach((AdminReservation r) -> assertTrue(r.slotStart() != null));
    }
}
