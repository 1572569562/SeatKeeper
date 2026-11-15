package cn.edu.stu.seatkeeper.model.po;

import cn.edu.stu.seatkeeper.model.enums.ReservationStatus;
import java.time.LocalDateTime;

/**
 * 管理端预约行：预约本体加上学生与座位信息，避免逐条回查。
 */
public record AdminReservationRow(long id, long userId, long seatId, LocalDateTime slotStart,
                                  LocalDateTime slotEnd, ReservationStatus status,
                                  LocalDateTime checkInDeadline, LocalDateTime checkedInAt, int version,
                                  String studentNo, String studentName,
                                  String room, int floor, String seatNo) { }
