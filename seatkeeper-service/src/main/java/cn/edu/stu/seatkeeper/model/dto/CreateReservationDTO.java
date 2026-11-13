package cn.edu.stu.seatkeeper.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.OffsetDateTime;

/**
 * 创建预约请求。
 *
 * @param seatId 座位标识
 * @param slotStart 预约起点，必须精确命中 slot 表中的固定时段
 */
public record CreateReservationDTO(@NotNull(message = "座位不能为空") @Positive(message = "座位标识必须为正数")
                                   Long seatId,
                                   @NotNull(message = "时段不能为空") OffsetDateTime slotStart) { }
