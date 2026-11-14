package com.seatkeeper.admin.repo;

import com.seatkeeper.admin.api.ApiException;
import com.seatkeeper.admin.domain.AdminReservation;
import com.seatkeeper.admin.domain.PageResult;
import com.seatkeeper.admin.domain.ReservationFilter;
import com.seatkeeper.admin.domain.ReservationStatus;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * 本地假预约数据：3 天 × 4 个时段 × 9 个座位，约 60 条。
 * 用固定种子生成，每次启动看到的一样，方便截图和演示。
 *
 * 过滤和分页逻辑照契约实现，这样接真接口时行为一致（含 hasMore）。
 */
public final class MockReservationRepository implements ReservationRepository {

    private static final ZoneOffset ZONE = ZoneOffset.ofHours(8);

    private record Student(String no, String name) {
    }

    private static final List<Student> STUDENTS = List.of(
            new Student("20230101", "张某"),
            new Student("20230102", "李某"),
            new Student("20230103", "王某"),
            new Student("20230104", "赵某"));

    private record SeatInfo(long id, int floor, String room, String seatNo) {
    }

    private static final List<SeatInfo> SEATS = List.of(
            new SeatInfo(1027, 1, "A区", "A-01"),
            new SeatInfo(1028, 1, "A区", "A-02"),
            new SeatInfo(1029, 1, "A区", "A-03"),
            new SeatInfo(1030, 1, "A区", "A-04"),
            new SeatInfo(1041, 1, "B区", "B-01"),
            new SeatInfo(1042, 1, "B区", "B-02"),
            new SeatInfo(1043, 1, "B区", "B-03"),
            new SeatInfo(1055, 2, "C区", "C-01"),
            new SeatInfo(1056, 2, "C区", "C-02"));

    private static final List<LocalTime> SLOT_STARTS = List.of(
            LocalTime.of(8, 0), LocalTime.of(10, 0), LocalTime.of(14, 0), LocalTime.of(16, 0));

    private static final List<LocalDate> DAYS = List.of(
            LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 14));

    private final List<AdminReservation> all = new ArrayList<>();

    public MockReservationRepository() {
        seed();
    }

    private void seed() {
        Random random = new Random(20261114L);
        long id = 8800;

        for (LocalDate day : DAYS) {
            for (LocalTime start : SLOT_STARTS) {
                for (SeatInfo seat : SEATS) {
                    // 约 4 成空位，让界面有「查不到」的情况
                    if (random.nextInt(10) < 4) {
                        continue;
                    }
                    id++;
                    Student student = STUDENTS.get(random.nextInt(STUDENTS.size()));
                    OffsetDateTime slotStart = OffsetDateTime.of(day, start, ZONE);
                    OffsetDateTime slotEnd = slotStart.plusHours(2);
                    OffsetDateTime deadline = slotStart.plusMinutes(15);

                    ReservationStatus status = pickStatus(random, day);
                    OffsetDateTime checkedIn = switch (status) {
                        case CHECKED_IN, COMPLETED -> slotStart.plusMinutes(1 + random.nextInt(10));
                        default -> null;
                    };

                    // 本地演示用：按学号后四位造一个稳定的用户 ID
                    long userId = 5000 + Long.parseLong(student.no().substring(6));
                    all.add(new AdminReservation(id, userId, student.no(), student.name(), seat.id(),
                            seat.seatNo(), seat.room(), seat.floor(), slotStart, slotEnd,
                            deadline, checkedIn, status, random.nextInt(3)));
                }
            }
        }

        all.sort(Comparator.comparing(AdminReservation::slotStart).reversed()
                .thenComparing(Comparator.comparingLong(AdminReservation::id).reversed()));
    }

    /** 过去的时段多是已完成/已过期，今天以后多为已预约 */
    private ReservationStatus pickStatus(Random random, LocalDate day) {
        boolean past = day.isBefore(LocalDate.of(2026, 11, 14));
        int roll = random.nextInt(100);
        if (past) {
            if (roll < 55) {
                return ReservationStatus.COMPLETED;
            }
            if (roll < 80) {
                return ReservationStatus.EXPIRED;
            }
            if (roll < 90) {
                return ReservationStatus.CANCELLED;
            }
            return ReservationStatus.CHECKED_IN;
        }
        if (roll < 70) {
            return ReservationStatus.RESERVED;
        }
        if (roll < 90) {
            return ReservationStatus.CHECKED_IN;
        }
        return ReservationStatus.CANCELLED;
    }

    @Override
    public PageResult<AdminReservation> search(ReservationFilter filter) {
        List<AdminReservation> matched = new ArrayList<>();
        for (AdminReservation r : all) {
            if (!matches(r, filter)) {
                continue;
            }
            matched.add(r);
        }

        int size = Math.max(1, filter.size());
        int page = Math.max(1, filter.page());
        int fromIndex = Math.min((page - 1) * size, matched.size());
        int toIndex = Math.min(fromIndex + size, matched.size());

        List<AdminReservation> items = matched.subList(fromIndex, toIndex);
        boolean hasMore = toIndex < matched.size();
        return new PageResult<>(new ArrayList<>(items), page, size, hasMore);
    }

    private boolean matches(AdminReservation r, ReservationFilter filter) {
        if (filter.studentNo() != null && !filter.studentNo().isBlank()
                && !r.studentNo().contains(filter.studentNo().trim())) {
            return false;
        }
        if (filter.seatId() != null && r.seatId() != filter.seatId()) {
            return false;
        }
        if (filter.status() != null && r.status() != filter.status()) {
            return false;
        }
        if (filter.from() != null && r.slotStart().isBefore(filter.from())) {
            return false;
        }
        if (filter.to() != null && !r.slotStart().isBefore(filter.to())) {
            return false;
        }
        return true;
    }

    @Override
    public AdminReservation forceCancel(long reservationId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ApiException("INVALID_ARGUMENT", "请填写取消原因");
        }
        for (int i = 0; i < all.size(); i++) {
            AdminReservation current = all.get(i);
            if (current.id() != reservationId) {
                continue;
            }
            if (!current.cancellable()) {
                throw new ApiException("INVALID_STATE",
                        "「" + current.status().getLabel() + "」状态不允许取消");
            }
            AdminReservation cancelled = current.withStatus(ReservationStatus.CANCELLED);
            all.set(i, cancelled);
            return cancelled;
        }
        throw new ApiException("NOT_FOUND", "预约不存在，可能已被删除");
    }

    /** 供测试和演示用 */
    public List<AdminReservation> all() {
        return List.copyOf(all);
    }
}
