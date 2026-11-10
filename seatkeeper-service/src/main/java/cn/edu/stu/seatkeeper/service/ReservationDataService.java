package cn.edu.stu.seatkeeper.service;
import cn.edu.stu.seatkeeper.model.po.ReservationPO;
import cn.edu.stu.seatkeeper.model.enums.ReservationStatus;
import cn.edu.stu.seatkeeper.exception.DataOperationException;
import java.time.LocalDateTime;
import java.util.List;

/** 成员四提供的持久化服务契约；事务和数据库访问由实现类承担。 */
public interface ReservationDataService {
    /**
     * 从固定时段创建预约，唯一约束决定最终互斥。
     * @param userId 用户标识
     * @param seatId 座位标识
     * @param slotStart 已定义时段的开始时间
     * @param checkInDeadline 签到截止时间
     * @return 新预约快照，初始状态为已预约且版本为零
     * @throws DataOperationException 时段未登记，或同座位、同用户已占用该活动时段
     * @throws IllegalArgumentException 用户或座位标识不是正整数
     * @throws NullPointerException 时段开始时间或签到截止时间为空
     */
    ReservationPO create(long userId, long seatId, LocalDateTime slotStart, LocalDateTime checkInDeadline);

    /**
     * 查询当前持久化快照，无记录时返回空。
     * @param id 记录标识
     * @return 查询或持久化结果
     */
    ReservationPO find(long id);

    /**
     * 分页读取指定用户的预约。
     * @param userId 用户标识
     * @param offset 从零开始的偏移
     * @param limit 批量上限，范围为一至一千
     * @return 查询或持久化结果
     */
    List<ReservationPO> listByUser(long userId, int offset, int limit);

    /**
     * 按归属和预期版本更新状态，上层负责状态机校验。
     * @param id 记录标识
     * @param userId 用户标识
     * @param expectedVersion 预期版本号
     * @param status 目标状态
     * @return 成功修改的行数
     * @throws DataOperationException 目标活动状态与已有预约发生唯一约束冲突
     */
    int changeStatus(long id, long userId, int expectedVersion, ReservationStatus status);
}
