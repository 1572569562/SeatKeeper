package cn.edu.stu.seatkeeper.model.po;

/**
 * 统计聚合行：总数、已使用（签到或完成）、爽约（过期）。
 *
 * @param total 预约总数
 * @param checkedIn 已签到或已完成
 * @param noShow 已过期
 */
public record ReservationStatsRow(long total, long checkedIn, long noShow) { }
