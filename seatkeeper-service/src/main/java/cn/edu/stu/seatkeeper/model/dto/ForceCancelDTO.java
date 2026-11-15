package cn.edu.stu.seatkeeper.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 强制取消请求。
 *
 * @param reason 操作原因，会写进通知正文
 */
public record ForceCancelDTO(@NotBlank(message = "原因不能为空")
                             @Size(max = 64, message = "原因不能超过六十四个字符") String reason) { }
