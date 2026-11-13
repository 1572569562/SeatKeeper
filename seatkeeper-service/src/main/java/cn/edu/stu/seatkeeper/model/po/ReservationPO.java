package cn.edu.stu.seatkeeper.model.po;

import cn.edu.stu.seatkeeper.model.enums.ReservationStatus;
import java.time.LocalDateTime;

/** 预约快照，version 随每次成功修改递增；checkedInAt 仅在签到后非空。 */
public record ReservationPO(long id, long userId, long seatId, LocalDateTime slotStart,
        LocalDateTime slotEnd, ReservationStatus status, LocalDateTime checkInDeadline,
        LocalDateTime checkedInAt, int version) { }
