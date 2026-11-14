package com.seatkeeper.admin.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 信用快照，字段与契约的 CreditVO 一致：{userId, score, restrictedUntil, version}
 *
 * 分数范围 0..100；低于 60 会被限制预约，restrictedUntil 记录解除时间。
 */
public record Credit(long userId, int score, OffsetDateTime restrictedUntil, int version) {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    public boolean isRestricted() {
        return restrictedUntil != null && restrictedUntil.toInstant().isAfter(Instant.now());
    }

    /** 分档说明，界面上给人看的 */
    public String levelText() {
        if (score >= 90) {
            return "良好";
        }
        if (score >= 60) {
            return "正常";
        }
        return "受限";
    }

    public String restrictedText() {
        if (restrictedUntil == null) {
            return "无";
        }
        if (!isRestricted()) {
            return "已解除（" + TIME.format(restrictedUntil) + "）";
        }
        long hours = Duration.between(Instant.now(), restrictedUntil.toInstant()).toHours();
        return "至 " + TIME.format(restrictedUntil) + "（还剩 " + Math.max(hours, 0) + " 小时）";
    }
}
