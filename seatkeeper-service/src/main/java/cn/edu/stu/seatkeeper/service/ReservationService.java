package cn.edu.stu.seatkeeper.service;

import cn.edu.stu.seatkeeper.model.enums.ReservationStatus;
import cn.edu.stu.seatkeeper.model.vo.CheckInVO;
import cn.edu.stu.seatkeeper.model.vo.ReservationPageVO;
import cn.edu.stu.seatkeeper.model.vo.ReservationVO;
import java.time.OffsetDateTime;

/** 预约用例：创建、查询、签到与取消。 */
public interface ReservationService {

    /**
     * 创建预约；校验顺序见 contracts/openapi.yaml 的描述。
     * @param userId 当前用户
     * @param seatId 座位标识
     * @param slotStart 预约起点
     * @return 新建预约
     */
    ReservationVO create(long userId, long seatId, OffsetDateTime slotStart);

    /**
     * @param userId 当前用户
     * @param status 状态过滤，可为空
     * @param page 页码，从 1 开始
     * @param size 每页数量
     * @return 分页结果
     */
    ReservationPageVO listMine(long userId, ReservationStatus status, int page, int size);

    /**
     * @param userId 当前用户
     * @param id 预约标识
     * @return 预约详情
     */
    ReservationVO findMine(long userId, long id);

    /**
     * 座位码签到，重复调用返回当前状态而不重复改状态。
     * @param userId 当前用户
     * @param id 预约标识
     * @param seatCode 座位码，必须与预约座位号一致
     * @return 签到结果
     */
    CheckInVO checkIn(long userId, long id, String seatCode);

    /**
     * 取消预约；仅 RESERVED 可取消，取消不返还信用分。
     * @param userId 当前用户
     * @param id 预约标识
     * @return 取消后的预约
     */
    ReservationVO cancel(long userId, long id);
}
