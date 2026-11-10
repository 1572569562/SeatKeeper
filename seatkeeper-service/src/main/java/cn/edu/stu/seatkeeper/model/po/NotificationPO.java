package cn.edu.stu.seatkeeper.model.po;

import java.time.LocalDateTime;

/** 面向指定用户的通知快照。 */
public record NotificationPO(long id, long userId, String type, String content, boolean readFlag,
        LocalDateTime createdAt) { }
