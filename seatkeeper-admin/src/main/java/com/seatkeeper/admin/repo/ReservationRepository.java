package com.seatkeeper.admin.repo;

import com.seatkeeper.admin.domain.AdminReservation;
import com.seatkeeper.admin.domain.PageResult;
import com.seatkeeper.admin.domain.ReservationFilter;

/**
 * 预约数据来源，对应契约：
 *   GET  /api/v1/admin/reservations
 *   POST /api/v1/admin/reservations/{id}/force-cancel  body {"reason": "..."}
 *
 * Mock 和 HTTP 两个实现，界面只认这个接口。
 */
public interface ReservationRepository {

    PageResult<AdminReservation> search(ReservationFilter filter);

    /**
     * 强制取消。原因必填（契约要求 ≤64 字符），会写进发给学生的通知正文。
     * 失败时抛 ApiException：INVALID_STATE（状态不允许）、NOT_FOUND（不存在）。
     */
    AdminReservation forceCancel(long reservationId, String reason);
}
