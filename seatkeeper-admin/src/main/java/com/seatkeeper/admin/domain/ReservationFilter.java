package com.seatkeeper.admin.domain;

import java.time.OffsetDateTime;

/**
 * 预约查询条件，对应 GET /api/v1/admin/reservations 的 query 参数。
 * 字段为空表示不按这个条件过滤。
 *
 * @param studentNo 学号，模糊匹配
 * @param seatId    座位标识，精确匹配
 * @param status    状态，精确匹配
 * @param from      slot_start 下界（含）
 * @param to        slot_start 上界（不含）
 * @param page      页码，从 1 开始
 * @param size      每页数量
 */
public record ReservationFilter(
        String studentNo,
        Long seatId,
        ReservationStatus status,
        OffsetDateTime from,
        OffsetDateTime to,
        int page,
        int size) {

    public static ReservationFilter firstPage(int size) {
        return new ReservationFilter(null, null, null, null, null, 1, size);
    }

    public ReservationFilter withPage(int newPage) {
        return new ReservationFilter(studentNo, seatId, status, from, to, newPage, size);
    }

    public ReservationFilter withSize(int newSize) {
        return new ReservationFilter(studentNo, seatId, status, from, to, 1, newSize);
    }
}
