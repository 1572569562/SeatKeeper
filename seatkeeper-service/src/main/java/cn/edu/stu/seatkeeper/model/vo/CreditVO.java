package cn.edu.stu.seatkeeper.model.vo;

import java.time.OffsetDateTime;

/**
 * 信用快照视图。
 *
 * @param userId 用户标识
 * @param score 当前分值，0..100
 * @param restrictedUntil 限制截止时间，可为空
 * @param version 乐观锁版本
 */
public record CreditVO(long userId, int score, OffsetDateTime restrictedUntil, int version) { }
