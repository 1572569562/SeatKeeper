package com.seatkeeper.admin.domain;

/**
 * 统计看板数据，字段与契约的 StatsVO 一致：
 * {totalReservations, checkedIn, noShow, checkInRate, noShowRate}
 *
 * checkedIn = 已签到 + 已完成；noShow = 已过期。
 * 两个比率都是 0..1 的小数，界面显示时转成百分比。
 *
 * 注意：noShow 依赖守护节点把超期未签到的预约置成 EXPIRED。
 * 守护节点没跑的时候，这个数会偏小（甚至为 0），不是客户端算错。
 */
public record Stats(
        long totalReservations,
        long checkedIn,
        long noShow,
        double checkInRate,
        double noShowRate) {

    public static Stats empty() {
        return new Stats(0, 0, 0, 0, 0);
    }

    public boolean isEmpty() {
        return totalReservations == 0;
    }

    /** 44.2% 这种写法 */
    public String checkInPercent() {
        return percent(checkInRate);
    }

    public String noShowPercent() {
        return percent(noShowRate);
    }

    private static String percent(double rate) {
        return String.format("%.1f%%", Math.max(0, Math.min(1, rate)) * 100);
    }
}
