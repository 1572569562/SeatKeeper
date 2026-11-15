package cn.edu.stu.seatkeeper.model.vo;

import java.util.List;

/**
 * 通知增量结果。
 *
 * @param items 本次消息
 * @param lastId 本次最大 id；无新消息时原样返回入参 afterId
 * @param hasMore 是否还有更多
 */
public record NotificationPageVO(List<NotificationVO> items, long lastId, boolean hasMore) { }
