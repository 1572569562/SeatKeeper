package cn.edu.stu.seatkeeper.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 管理员信用调整请求。
 *
 * <p>受 {@code credit_log.reservation_id NOT NULL} 与 {@code uk_credit_res_reason} 约束，
 * 每次调整必须绑定该用户的一条预约作为流水归属，且同一预约同一原因只能调整一次。
 *
 * @param reservationId 归属预约，必须属于被调整用户
 * @param delta 分值变化，正数加分、负数扣分
 * @param reason 稳定原因码，最长 64 字符，区分大小写
 */
public record CreditAdjustDTO(@NotNull(message = "归属预约不能为空")
                              @Positive(message = "预约标识必须为正数")
                              Long reservationId,
                              @NotNull(message = "分值变化不能为空")
                              @Min(value = -100, message = "单次最多扣 100 分")
                              @Max(value = 100, message = "单次最多加 100 分")
                              Integer delta,
                              @NotBlank(message = "原因不能为空")
                              @Size(max = 64, message = "原因不能超过六十四个字符")
                              String reason) { }
