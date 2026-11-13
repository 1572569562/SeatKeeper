package cn.edu.stu.seatkeeper.service;

import cn.edu.stu.seatkeeper.model.enums.ReservationStatus;
import cn.edu.stu.seatkeeper.model.vo.AdminReservationPageVO;
import cn.edu.stu.seatkeeper.model.vo.AdminReservationVO;
import cn.edu.stu.seatkeeper.model.vo.SeatVO;
import cn.edu.stu.seatkeeper.model.vo.StatsVO;
import java.time.OffsetDateTime;
import java.util.List;

/** 管理端用例：座位状态、预约查询、强制取消与统计。 */
public interface AdminService {

    /**
     * @param room 阅览室，可为空
     * @param floor 楼层，可为空
     * @param status 状态，可为空
     * @return 座位列表
     */
    List<SeatVO> listSeats(String room, Integer floor, String status);

    /**
     * @param seatId 座位标识
     * @param status 目标状态 AVAILABLE / DISABLED
     * @return 更新后的座位
     */
    SeatVO updateSeatStatus(long seatId, String status);

    /**
     * @param studentNo 学号，可为空
     * @param seatId 座位标识，可为空
     * @param status 状态，可为空
     * @param from 时段下界，可为空
     * @param to 时段上界，可为空
     * @param page 页码
     * @param size 每页数量
     * @return 分页结果
     */
    AdminReservationPageVO search(String studentNo, Long seatId, ReservationStatus status,
                                  OffsetDateTime from, OffsetDateTime to, int page, int size);

    /**
     * 强制取消：不扣分也不返还，向学生发送 SYSTEM 通知。
     * @param id 预约标识
     * @param reason 操作原因
     * @return 取消后的预约
     */
    AdminReservationVO forceCancel(long id, String reason);

    /**
     * @param from 时段下界，可为空
     * @param to 时段上界，可为空
     * @return 统计看板
     */
    StatsVO stats(OffsetDateTime from, OffsetDateTime to);
}
