package com.seatkeeper.admin.repo;

import com.seatkeeper.admin.domain.Stats;

import java.time.OffsetDateTime;

/**
 * 统计看板数据来源，对应契约 GET /api/v1/admin/stats?from&to。
 *
 * from / to 都可以为空，为空表示不限。
 */
public interface StatsRepository {

    Stats load(OffsetDateTime from, OffsetDateTime to);
}
