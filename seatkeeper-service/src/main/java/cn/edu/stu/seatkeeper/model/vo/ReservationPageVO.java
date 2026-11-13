package cn.edu.stu.seatkeeper.model.vo;

import java.util.List;

/**
 * 预约分页结果；hasMore 由"多取一条"判断，不额外执行 count 查询。
 *
 * @param items 当前页
 * @param page 页码，从 1 开始
 * @param size 每页数量
 * @param hasMore 是否还有下一页
 */
public record ReservationPageVO(List<ReservationVO> items, int page, int size, boolean hasMore) { }
