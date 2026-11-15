package cn.edu.stu.seatkeeper.model.vo;

import cn.edu.stu.seatkeeper.model.enums.ReservationStatus;
import java.time.OffsetDateTime;

/**
 * 管理端预约视图，额外包含学生信息。
 *
 * @param id 预约标识
 * @param studentNo 学号
 * @param studentName 姓名
 * @param seatId 座位标识
 * @param seatNo 座位号
 * @param room 阅览室
 * @param floor 楼层
 * @param slotStart 时段开始
 * @param slotEnd 时段结束
 * @param checkInDeadline 签到截止
 * @param checkedInAt 签到时间，可为空
 * @param status 状态
 * @param version 乐观锁版本
 */
public record AdminReservationVO(long id, String studentNo, String studentName,
                                 long seatId, String seatNo, String room, int floor,
                                 OffsetDateTime slotStart, OffsetDateTime slotEnd,
                                 OffsetDateTime checkInDeadline, OffsetDateTime checkedInAt,
                                 ReservationStatus status, int version) { }
