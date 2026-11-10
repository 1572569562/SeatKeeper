package cn.edu.stu.seatkeeper.model.po;

/** 座位查询结果。 */
public record SeatPO(long id, String room, int floor, String seatNo, String status) { }
