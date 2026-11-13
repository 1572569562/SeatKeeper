package cn.edu.stu.seatkeeper.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 签到请求。
 *
 * @param seatCode 座位码，必须与预约座位的 seat_no 一致
 */
public record CheckInDTO(@NotBlank(message = "座位码不能为空") @Size(max = 32, message = "座位码不能超过三十二个字符")
                         String seatCode) { }
