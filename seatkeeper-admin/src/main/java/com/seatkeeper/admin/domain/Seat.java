package com.seatkeeper.admin.domain;

/**
 * 一个座位，字段与契约里的 SeatVO 一一对应：
 * {@code {id, room, floor, seatNo, status}}
 *
 * floor 是整数（1、2…），不是字符串「1楼」，界面显示时再拼。
 */
public record Seat(long id, String room, int floor, String seatNo, SeatStatus status) {

    public Seat withStatus(SeatStatus newStatus) {
        return new Seat(id, room, floor, seatNo, newStatus);
    }

    /** 楼层显示名，例如 1 -> "1楼" */
    public String floorName() {
        return floor + "楼";
    }

    /** 完整位置，例如「1楼 A区 A-03」 */
    public String location() {
        return floorName() + " " + room + " " + seatNo;
    }
}
