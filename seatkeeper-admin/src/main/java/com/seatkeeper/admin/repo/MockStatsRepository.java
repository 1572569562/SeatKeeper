package com.seatkeeper.admin.repo;

import com.seatkeeper.admin.domain.AdminReservation;
import com.seatkeeper.admin.domain.ReservationStatus;
import com.seatkeeper.admin.domain.Stats;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 本地统计：直接拿假预约数据算，保证和「预约查询」页看到的数字一致。
 *
 * 口径按契约：
 *   总数     = 区间内全部预约
 *   已签到   = CHECKED_IN + COMPLETED
 *   爽约     = EXPIRED
 *   签到率   = 已签到 / 总数
 *   爽约率   = 爽约 / 总数
 */
public final class MockStatsRepository implements StatsRepository {

    private final MockReservationRepository reservations;

    public MockStatsRepository(MockReservationRepository reservations) {
        this.reservations = reservations;
    }

    @Override
    public Stats load(OffsetDateTime from, OffsetDateTime to) {
        List<AdminReservation> all = reservations.all();

        long total = 0;
        long checkedIn = 0;
        long noShow = 0;

        for (AdminReservation r : all) {
            if (from != null && r.slotStart().isBefore(from)) {
                continue;
            }
            if (to != null && !r.slotStart().isBefore(to)) {
                continue;
            }
            total++;
            if (r.status() == ReservationStatus.CHECKED_IN || r.status() == ReservationStatus.COMPLETED) {
                checkedIn++;
            } else if (r.status() == ReservationStatus.EXPIRED) {
                noShow++;
            }
        }

        if (total == 0) {
            return Stats.empty();
        }
        return new Stats(total, checkedIn, noShow,
                (double) checkedIn / total, (double) noShow / total);
    }
}
