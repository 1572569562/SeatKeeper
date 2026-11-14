package com.seatkeeper.admin.repo;

import com.seatkeeper.admin.api.ApiException;
import com.seatkeeper.admin.domain.Credit;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

/**
 * 本地假信用数据。默认 100 分，最多扣到 0、加到 100。
 * 低于 60 分时按契约规则限制预约 7 天。
 */
public final class MockCreditRepository implements CreditRepository {

    private static final ZoneOffset ZONE = ZoneOffset.ofHours(8);
    private static final int LIMIT_THRESHOLD = 60;

    private final Map<Long, Credit> credits = new HashMap<>();

    @Override
    public Credit get(long userId) {
        return credits.computeIfAbsent(userId, id -> new Credit(id, 100, null, 0));
    }

    @Override
    public Credit adjust(long userId, long reservationId, int delta, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ApiException("INVALID_ARGUMENT", "请填写调整原因");
        }
        if (delta == 0) {
            throw new ApiException("INVALID_ARGUMENT", "分值变化不能为 0");
        }
        if (reservationId <= 0) {
            throw new ApiException("INVALID_ARGUMENT", "必须绑定一条预约作为流水归属");
        }

        Credit current = get(userId);
        int score = Math.max(0, Math.min(100, current.score() + delta));
        if (score == current.score() && (current.score() == 0 || current.score() == 100)) {
            throw new ApiException("CREDIT_OUT_OF_RANGE", "信用分已经在 " + score + "，不能再调整");
        }

        OffsetDateTime restrictedUntil = score < LIMIT_THRESHOLD
                ? OffsetDateTime.now(ZONE).plusDays(7)
                : current.restrictedUntil();

        Credit updated = new Credit(userId, score, restrictedUntil, current.version() + 1);
        credits.put(userId, updated);
        return updated;
    }
}
