package cn.edu.stu.seatkeeper.model.po;

import java.time.LocalDateTime;

/** 已提交的信用变动；幂等重放返回同一条流水。 */
public record CreditLogPO(long id, long userId, long reservationId, int delta, String reason,
        int balanceAfter, LocalDateTime createdAt) { }
