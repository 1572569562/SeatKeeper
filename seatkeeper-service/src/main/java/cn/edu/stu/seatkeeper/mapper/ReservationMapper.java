package cn.edu.stu.seatkeeper.mapper;

import cn.edu.stu.seatkeeper.model.po.ReservationPO;
import cn.edu.stu.seatkeeper.model.enums.ReservationStatus;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 唯一索引负责活动预约的最终互斥。 */
@Mapper
public interface ReservationMapper {
    /**
     * 从已登记时段取得结束时间并插入预约，未登记时段返回零行。
     * @param userId 用户标识
     * @param seatId 座位标识
     * @param slotStart 已登记时段的开始时间
     * @param deadline 签到截止时间
     */
    int insertFromSlot(@Param("userId") long userId, @Param("seatId") long seatId,
            @Param("slotStart") LocalDateTime slotStart, @Param("deadline") LocalDateTime deadline);
    /**
     * 取得当前事务所用连接最近插入的自增标识，必须紧接同连接插入调用。
     */
    long lastInsertId();
    /**
     * 按预约标识读取持久化快照，无记录时返回空；对外调用需由上层核验归属。
     * @param id 记录标识
     */
    ReservationPO find(@Param("id") long id);
    /**
     * 按时段和标识倒序分页读取指定用户的预约。
     * @param userId 用户标识
     * @param offset 从零开始的分页偏移
     * @param limit 本批或本页数量上限
     */
    List<ReservationPO> listByUser(@Param("userId") long userId, @Param("offset") int offset, @Param("limit") int limit);
    /**
     * 按预约归属和预期版本更新状态，并在成功时递增版本号。
     * @param id 记录标识
     * @param userId 用户标识
     * @param expectedVersion 预期版本号
     * @param status 对应的状态值
     */
    int changeStatus(@Param("id") long id, @Param("userId") long userId,
            @Param("expectedVersion") int expectedVersion, @Param("status") ReservationStatus status);
}
