package cn.edu.stu.seatkeeper.model.vo;

import java.time.OffsetDateTime;

/**
 * 通知视图。
 *
 * @param id 通知标识，同时作为游标
 * @param type 类型：RESERVATION / CHECKIN / CREDIT / SYSTEM
 * @param content 正文
 * @param readFlag 是否已读
 * @param createdAt 创建时间（带 +08:00）
 */
public record NotificationVO(long id, String type, String content, boolean readFlag,
                             OffsetDateTime createdAt) { }
