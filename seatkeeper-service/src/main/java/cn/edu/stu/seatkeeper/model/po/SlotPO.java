package cn.edu.stu.seatkeeper.model.po;

import java.time.LocalDateTime;

/** 预先配置的固定预约时段。 */
public record SlotPO(long id, LocalDateTime startTime, LocalDateTime endTime) { }
