package cn.edu.stu.seatkeeper.model.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 标记已读请求。
 *
 * @param ids 通知标识集合，最多 100 条
 */
public record MarkReadDTO(@NotEmpty(message = "通知标识不能为空")
                          @Size(max = 100, message = "一次最多标记 100 条") List<Long> ids) { }
