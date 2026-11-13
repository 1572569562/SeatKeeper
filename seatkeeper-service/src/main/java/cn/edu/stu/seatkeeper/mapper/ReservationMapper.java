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
    /**
     * 统计用户当前仍占用中的预约数量，用于"同时最多 2 个"的业务校验。
     * @param userId 用户标识
     * @param now 以服务端时间为准的比较基准
     */
    int countActiveByUser(@Param("userId") long userId, @Param("now") LocalDateTime now);
    /**
     * 按可选状态过滤并分页读取用户预约，倒序与 listByUser 一致。
     * @param userId 用户标识
     * @param status 状态过滤，可为空
     * @param offset 从零开始的分页偏移
     * @param limit 本页数量上限
     */
    List<ReservationPO> listByUserAndStatus(@Param("userId") long userId,
            @Param("status") ReservationStatus status, @Param("offset") int offset, @Param("limit") int limit);
    /**
     * 仅把 RESERVED 且版本匹配的预约置为 CHECKED_IN，并记录首次签到时间。
     * @param id 预约标识
     * @param userId 用户标识
     * @param expectedVersion 预期版本
     * @param now 服务端当前时间
     */
    int markCheckedIn(@Param("id") long id, @Param("userId") long userId,
            @Param("expectedVersion") int expectedVersion, @Param("now") LocalDateTime now);
    /**
     * 扫描已过签到截止仍未签到的预约，供守护节点批处理。
     * @param now 服务端当前时间
     * @param limit 本批上限
     */
    List<ReservationPO> listExpiredCandidates(@Param("now") LocalDateTime now, @Param("limit") int limit);
    /**
     * 扫描已结束仍处于使用中的预约，供守护节点批处理。
     * @param now 服务端当前时间
     * @param limit 本批上限
     */
    List<ReservationPO> listCompletedCandidates(@Param("now") LocalDateTime now, @Param("limit") int limit);
    /**
     * 管理端多条件查询，附带学生与座位信息。
     * @param studentNo 学号，可为空
     * @param seatId 座位标识，可为空
     * @param status 状态，可为空
     * @param from 时段下界（含），可为空
     * @param to 时段上界（不含），可为空
     * @param offset 分页偏移
     * @param limit 本页上限
     */
    List<cn.edu.stu.seatkeeper.model.po.AdminReservationRow> searchAdmin(
            @Param("studentNo") String studentNo, @Param("seatId") Long seatId,
            @Param("status") ReservationStatus status, @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to, @Param("offset") int offset, @Param("limit") int limit);
    /**
     * 按主键读取管理端行（含学生与座位信息）。
     * @param id 预约标识
     */
    cn.edu.stu.seatkeeper.model.po.AdminReservationRow findAdminRow(@Param("id") long id);
    /**
     * 管理端状态变更：只按主键与版本匹配，不校验用户归属。
     * @param id 预约标识
     * @param expectedVersion 预期版本
     * @param status 目标状态
     */
    int changeStatusAsAdmin(@Param("id") long id, @Param("expectedVersion") int expectedVersion,
            @Param("status") ReservationStatus status);
    /**
     * 统计区间内的预约、签到与爽约数量。
     * @param from 时段下界（含），可为空
     * @param to 时段上界（不含），可为空
     */
    cn.edu.stu.seatkeeper.model.po.ReservationStatsRow countStats(
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
