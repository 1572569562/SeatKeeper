package cn.edu.stu.seatkeeper.model.vo;

/**
 * 座位视图。
 *
 * @param id 座位标识
 * @param room 阅览室
 * @param floor 楼层
 * @param seatNo 座位号，同时作为签到码
 * @param status AVAILABLE 或 DISABLED
 */
public record SeatVO(long id, String room, int floor, String seatNo, String status) { }
