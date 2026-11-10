package cn.edu.stu.seatkeeper.model.po;

import java.time.LocalDateTime;

/** 信用余额与限制截止时间快照。 */
public record CreditPO(long userId, int score, LocalDateTime restrictedUntil, int version) { }
