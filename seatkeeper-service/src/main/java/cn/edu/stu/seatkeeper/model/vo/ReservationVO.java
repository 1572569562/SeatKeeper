package cn.edu.stu.seatkeeper.model.vo;

import cn.edu.stu.seatkeeper.model.enums.ReservationStatus;
import java.time.OffsetDateTime;

/**
 * 预约视图，字段与 contracts/openapi.yaml 的 ReservationVO 一致。
 *
 * @param id 预约标识
 * @param seatId 座位标识
 * @param seatNo 座位号
 * @param room 阅览室
 * @param floor 楼层
 * @param slotStart 时段开始
 * @param slotEnd 时段结束
 * @param checkInDeadline 签到截止
 * @param status 状态
 * @param version 乐观锁版本
 */
public record ReservationVO(long id, long seatId, String seatNo, String room, int floor,
                            OffsetDateTime slotStart, OffsetDateTime slotEnd, OffsetDateTime checkInDeadline,
                            ReservationStatus status, int version) { }
