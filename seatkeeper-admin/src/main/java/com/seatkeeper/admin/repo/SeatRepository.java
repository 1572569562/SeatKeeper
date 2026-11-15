package com.seatkeeper.admin.repo;

import com.seatkeeper.admin.domain.Seat;
import com.seatkeeper.admin.domain.SeatStatus;

import java.util.List;
import java.util.Optional;

/**
 * 座位数据来源。界面只依赖这个接口：
 * - MockSeatRepository：本地假数据，服务端没起来时用
 * - HttpSeatRepository：调 /api/v1/admin/seats
 *
 * 对应契约（contracts/openapi.yaml）：
 *   GET   /api/v1/admin/seats?room&floor&status
 *   PATCH /api/v1/admin/seats/{id}   body {"status":"DISABLED"}
 */
public interface SeatRepository {

    /** 有哪些楼层，升序 */
    List<Integer> floors();

    /** 某楼层下的阅览室 */
    List<String> roomsOf(int floor);

    /** 某楼层某阅览室的座位 */
    List<Seat> seatsOf(int floor, String room);

    /** 改座位状态（只能 AVAILABLE / DISABLED），返回改完的座位 */
    Optional<Seat> updateStatus(long seatId, SeatStatus newStatus);

    /** 丢掉缓存重新拉一遍；本地假数据是空操作 */
    default void refresh() {
        // 默认什么都不做
    }
}
