package cn.edu.stu.seatkeeper.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 批处理请求；limit 缺省由服务层按 100 处理。
 *
 * @param limit 本批最多处理多少条
 */
public record BatchDTO(@Min(value = 1, message = "批量至少为 1")
                       @Max(value = 200, message = "批量最多 200") Integer limit) { }
