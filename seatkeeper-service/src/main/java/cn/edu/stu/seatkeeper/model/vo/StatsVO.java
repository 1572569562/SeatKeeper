package cn.edu.stu.seatkeeper.model.vo;

/**
 * 统计看板。
 *
 * @param totalReservations 区间内预约总数
 * @param checkedIn 已签到或已完成
 * @param noShow 已过期
 * @param checkInRate 签到率，0..1
 * @param noShowRate 爽约率，0..1
 */
public record StatsVO(long totalReservations, long checkedIn, long noShow,
                      double checkInRate, double noShowRate) { }
