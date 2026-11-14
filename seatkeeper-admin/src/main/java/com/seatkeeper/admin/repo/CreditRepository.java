package com.seatkeeper.admin.repo;

import com.seatkeeper.admin.domain.Credit;

/**
 * 信用数据来源，对应契约：
 *   GET  /api/v1/admin/credit/{userId}
 *   POST /api/v1/admin/credit/{userId}/adjust  body {reservationId, delta, reason}
 *
 * 调整必须绑定该用户的一条预约（credit_log.reservation_id NOT NULL），
 * 且同一预约 + 同一原因只能调一次（uk_credit_res_reason）。
 */
public interface CreditRepository {

    Credit get(long userId);

    Credit adjust(long userId, long reservationId, int delta, String reason);
}
