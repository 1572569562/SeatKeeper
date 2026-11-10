package cn.edu.stu.seatkeeper.service.impl;

import cn.edu.stu.seatkeeper.mapper.ReservationMapper;
import cn.edu.stu.seatkeeper.model.po.ReservationPO;
import cn.edu.stu.seatkeeper.model.enums.ReservationStatus;
import cn.edu.stu.seatkeeper.exception.DataOperationException;
import cn.edu.stu.seatkeeper.utils.ParameterCheckUtil;
import cn.edu.stu.seatkeeper.service.ReservationDataService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 预约持久化原语；认证、开放时间、签到期限与状态机由上层负责。 */
@Service
@Transactional(readOnly = true)
public class ReservationDataServiceImpl implements ReservationDataService {
    private final ReservationMapper mapper;

    /**
     * 通过构造器注入所需持久化接口，数据库操作和事务交由Spring管理。
     * @param mapper 当前业务的持久化映射接口
     */
    public ReservationDataServiceImpl(ReservationMapper mapper) { this.mapper = mapper; }

    /**
     * 以固定时段创建预约，将数据库唯一约束冲突转换为稳定的领域错误。
     * @param userId 用户标识
     * @param seatId 座位标识
     * @param slotStart 已登记时段的开始时间
     * @param checkInDeadline 签到截止时间
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public ReservationPO create(long userId, long seatId, LocalDateTime slotStart, LocalDateTime checkInDeadline) {
        ParameterCheckUtil.validateId(userId);
        ParameterCheckUtil.validateId(seatId);
        Objects.requireNonNull(slotStart, "时段开始时间不能为空");
        Objects.requireNonNull(checkInDeadline, "签到截止时间不能为空");
        // 不先查询空闲再插入：直接交给数据库唯一索引裁决竞争，避免检查与写入之间的竞态。
        try {
            if (mapper.insertFromSlot(userId, seatId, slotStart, checkInDeadline) == 0) {
                throw new DataOperationException(DataOperationException.Code.SLOT_NOT_FOUND, "固定预约时段不存在");
            }
            // 同一事务绑定同一连接，LAST_INSERT_ID 不会串到其他线程。
            return mapper.find(mapper.lastInsertId());
        } catch (DuplicateKeyException exception) {
            // 仅把唯一键冲突转换为预约冲突，连接异常等其他数据库错误继续向上传递。
            throw new DataOperationException(DataOperationException.Code.RESERVATION_CONFLICT, "该座位或用户已占用同一时段", exception);
        }
    }

    /**
     * 按预约标识读取持久化快照，无记录时返回空；对外调用需由上层核验归属。
     * @param id 记录标识
     */
    @Override
    public ReservationPO find(long id) {
        ParameterCheckUtil.validateId(id);
        return mapper.find(id);
    }

    /**
     * 按时段和标识倒序分页读取指定用户的预约。
     * @param userId 用户标识
     * @param offset 从零开始的分页偏移
     * @param limit 本批或本页数量上限
     */
    @Override
    public List<ReservationPO> listByUser(long userId, int offset, int limit) {
        ParameterCheckUtil.validateId(userId);
        ParameterCheckUtil.validatePage(offset, limit);
        return mapper.listByUser(userId, offset, limit);
    }

    /** 上层先校验业务状态流转，返回零行表示旧版本、无记录或归属不符。 */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int changeStatus(long id, long userId, int expectedVersion, ReservationStatus status) {
        ParameterCheckUtil.validateId(id);
        ParameterCheckUtil.validateId(userId);
        if (expectedVersion < 0) throw new IllegalArgumentException("版本不能小于零");
        Objects.requireNonNull(status, "状态不能为空");
        try {
            // 预约标识、用户归属和旧版本必须同时匹配；返回零行时由上层决定如何处理。
            return mapper.changeStatus(id, userId, expectedVersion, status);
        } catch (DuplicateKeyException exception) {
            // 仅把唯一键冲突转换为预约冲突，连接异常等其他数据库错误继续向上传递。
            throw new DataOperationException(DataOperationException.Code.RESERVATION_CONFLICT, "目标活动状态与现有预约冲突", exception);
        }
    }
}
