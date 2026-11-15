package com.seatkeeper.admin.domain;

/**
 * 座位状态，取值与 contracts/openapi.yaml 的 SeatStatus 一致。
 *
 * 注意：这里只有「可用 / 停用」两种。
 * 契约里 seat.status 表示「这个座位能不能被约」，跟是否有人约走无关；
 * 「已预约」是 reservation 表的事，管理端要看的话查 /api/v1/admin/reservations。
 */
public enum SeatStatus {

    AVAILABLE("可用", "available"),
    DISABLED("停用", "disabled");

    private final String label;
    private final String cssSuffix;

    SeatStatus(String label, String cssSuffix) {
        this.label = label;
        this.cssSuffix = cssSuffix;
    }

    public String getLabel() {
        return label;
    }

    /** 对应 admin.css 里的 .seat-available / .seat-disabled */
    public String getCssSuffix() {
        return cssSuffix;
    }

    /** 转成接口里的字符串：AVAILABLE / DISABLED */
    public String code() {
        return name();
    }

    public static SeatStatus ofCode(String code) {
        if (code == null) {
            return AVAILABLE;
        }
        return switch (code.trim().toUpperCase()) {
            case "DISABLED", "DISABLE" -> DISABLED;
            default -> AVAILABLE;
        };
    }
}
