package cn.edu.stu.seatkeeper.model.vo;

import cn.edu.stu.seatkeeper.model.enums.ReservationStatus;
import java.time.OffsetDateTime;

/**
 * 签到结果。
 *
 * @param status 签到后的状态
 * @param checkedInAt 首次签到时间
 */
public record CheckInVO(ReservationStatus status, OffsetDateTime checkedInAt) { }
