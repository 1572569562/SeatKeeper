package com.seatkeeper.admin.domain;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 管理端预约视图，字段与契约的 AdminReservationVO 一一对应。
 *
 * userId 目前契约里**没有**返回，所以可能是 null。
 * 信用调整接口是 /admin/credit/{userId}，需要 userId，
 * 这块等成员3 在 VO 里补字段；补上之前界面会提示手动填写。
 */
public record AdminReservation(
        long id,
        Long userId,
        String studentNo,
        String studentName,
        long seatId,
        String seatNo,
        String room,
        int floor,
        OffsetDateTime slotStart,
        OffsetDateTime slotEnd,
        OffsetDateTime checkInDeadline,
        OffsetDateTime checkedInAt,
        ReservationStatus status,
        int version) {

    private static final DateTimeFormatter SLOT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    public String floorName() {
        return floor + "楼";
    }

    public String seatText() {
        return floorName() + " " + room + " " + seatNo;
    }

    /** 时段显示成「11-14 08:00 ~ 09:00」 */
    public String slotText() {
        if (slotStart == null || slotEnd == null) {
            return "";
        }
        return SLOT.format(slotStart) + " ~ " + slotEnd.format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    public String checkedInText() {
        return checkedInAt == null ? "—" : SLOT.format(checkedInAt);
    }

    /** 只有「已预约 / 使用中」才允许强制取消，其它状态服务端会返回 409 */
    public boolean cancellable() {
        return status == ReservationStatus.RESERVED || status == ReservationStatus.CHECKED_IN;
    }

    /** 改状态并升版本号（本地假数据用；真接口以服务端返回为准） */
    public AdminReservation withStatus(ReservationStatus newStatus) {
        return new AdminReservation(id, userId, studentNo, studentName, seatId, seatNo, room, floor,
                slotStart, slotEnd, checkInDeadline, checkedInAt, newStatus, version + 1);
    }
}
