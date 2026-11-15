package cn.edu.stu.seatkeeper.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录请求。
 *
 * @param studentNo 学号
 * @param password 明文密码，仅在本次请求内存在
 */
public record LoginDTO(@NotBlank(message = "学号不能为空") @Size(max = 32, message = "学号不能超过三十二个字符")
                       String studentNo,
                       @NotBlank(message = "密码不能为空") @Size(max = 64, message = "密码不能超过六十四个字符")
                       String password) { }
