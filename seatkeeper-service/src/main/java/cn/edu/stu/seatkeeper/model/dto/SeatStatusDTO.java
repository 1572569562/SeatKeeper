package cn.edu.stu.seatkeeper.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 座位状态变更请求。
 *
 * @param status 目标状态，仅 AVAILABLE / DISABLED
 */
public record SeatStatusDTO(@NotBlank(message = "状态不能为空")
                            @Pattern(regexp = "AVAILABLE|DISABLED", message = "状态只能是 AVAILABLE 或 DISABLED")
                            String status) { }
