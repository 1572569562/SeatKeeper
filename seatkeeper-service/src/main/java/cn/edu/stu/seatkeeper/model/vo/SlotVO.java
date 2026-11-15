package cn.edu.stu.seatkeeper.model.vo;

import java.time.OffsetDateTime;

/**
 * 固定时段视图。
 *
 * @param id 时段标识
 * @param startTime 开始时间（带 +08:00）
 * @param endTime 结束时间（带 +08:00）
 */
public record SlotVO(long id, OffsetDateTime startTime, OffsetDateTime endTime) { }
