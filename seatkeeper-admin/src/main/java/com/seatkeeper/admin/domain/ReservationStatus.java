package com.seatkeeper.admin.domain;

/**
 * 预约状态，取值与契约的 ReservationStatus 一致：
 * RESERVED / CHECKED_IN / CANCELLED / EXPIRED / COMPLETED
 */
public enum ReservationStatus {

    RESERVED("已预约", "reserved"),
    CHECKED_IN("使用中", "checked"),
    CANCELLED("已取消", "cancelled"),
    EXPIRED("已过期", "expired"),
    COMPLETED("已完成", "completed");

    private final String label;
    private final String cssSuffix;

    ReservationStatus(String label, String cssSuffix) {
        this.label = label;
        this.cssSuffix = cssSuffix;
    }

    public String getLabel() {
        return label;
    }

    public String getCssSuffix() {
        return cssSuffix;
    }

    public String code() {
        return name();
    }

    public static ReservationStatus ofCode(String code) {
        if (code == null || code.isBlank()) {
            return RESERVED;
        }
        try {
            return valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return RESERVED;
        }
    }
}
