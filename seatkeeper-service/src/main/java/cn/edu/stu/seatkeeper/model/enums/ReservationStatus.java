package cn.edu.stu.seatkeeper.model.enums;

/** 数据库允许的预约状态；合法业务流转由成员3的状态机决定。 */
public enum ReservationStatus { RESERVED, CHECKED_IN, CANCELLED, EXPIRED, COMPLETED }
