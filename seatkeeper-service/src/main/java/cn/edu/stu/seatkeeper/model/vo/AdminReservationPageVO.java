package cn.edu.stu.seatkeeper.model.vo;

import java.util.List;

/**
 * 管理端预约分页。
 *
 * @param items 当前页
 * @param page 页码
 * @param size 每页数量
 * @param hasMore 是否还有下一页
 */
public record AdminReservationPageVO(List<AdminReservationVO> items, int page, int size, boolean hasMore) { }
