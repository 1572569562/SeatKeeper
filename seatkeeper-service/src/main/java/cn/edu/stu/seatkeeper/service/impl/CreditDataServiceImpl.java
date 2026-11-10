package cn.edu.stu.seatkeeper.service.impl;

import cn.edu.stu.seatkeeper.mapper.CreditMapper;
import cn.edu.stu.seatkeeper.mapper.ReservationMapper;
import cn.edu.stu.seatkeeper.model.po.CreditLogPO;
import cn.edu.stu.seatkeeper.model.po.CreditPO;
import cn.edu.stu.seatkeeper.model.po.ReservationPO;
import cn.edu.stu.seatkeeper.exception.DataOperationException;
import cn.edu.stu.seatkeeper.utils.ParameterCheckUtil;
import cn.edu.stu.seatkeeper.service.CreditDataService;

import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 信用余额和流水的唯一事务入口。 */
@Service
@Transactional(readOnly = true)
public class CreditDataServiceImpl implements CreditDataService {
    private final CreditMapper mapper;
    private final ReservationMapper reservations;

    /**
     * 通过构造器注入所需持久化接口，数据库操作和事务交由Spring管理。
     * @param mapper 当前业务的持久化映射接口
     * @param reservations 预约查询映射接口
     */
    public CreditDataServiceImpl(CreditMapper mapper, ReservationMapper reservations) {
        this.mapper = mapper;
        this.reservations = reservations;
    }

    /**
     * 按用户标识读取信用余额、限制截止时间和版本，无账户时返回空。
     * @param userId 用户标识
     */
    @Override
    public CreditPO find(long userId) {
        ParameterCheckUtil.validateId(userId);
        return mapper.find(userId);
    }

    /**
     * 核验归属后锁定信用行，在同一事务内完成幂等判断、余额更新和流水写入。
     * @param userId 用户标识
     * @param reservationId 预约标识
     * @param delta 信用分变化量
     * @param reason 区分大小写的幂等原因
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public CreditLogPO applyDelta(long userId, long reservationId, int delta, String reason) {
        ParameterCheckUtil.validateId(userId);
        ParameterCheckUtil.validateId(reservationId);
        ParameterCheckUtil.validateText(reason, 64);
        // 先验证预约归属，防止把其他用户的预约作为当前用户信用流水的幂等凭据。
        ReservationPO reservation = reservations.find(reservationId);
        if (reservation == null) throw new DataOperationException(DataOperationException.Code.NOT_FOUND, "预约不存在");
        if (reservation.userId() != userId) {
            throw new DataOperationException(DataOperationException.Code.OWNERSHIP_MISMATCH, "信用变动用户与预约归属不一致");
        }
        // 同一用户的信用写入先竞争同一主键行锁，后续读流水、改余额和写流水使用同一事务。
        CreditPO credit = mapper.lockByUser(userId);
        if (credit == null) throw new DataOperationException(DataOperationException.Code.NOT_FOUND, "用户信用账户不存在");
        // 加锁后读取最新流水：相同请求直接重放已有结果，变更分值的重放必须拒绝。
        CreditLogPO existing = mapper.findLog(reservationId, reason);
        if (existing != null) {
            if (existing.userId() != userId || existing.delta() != delta) {
                throw new DataOperationException(DataOperationException.Code.IDEMPOTENCY_MISMATCH, "幂等重放的用户或分值发生变化");
            }
            return existing;
        }
        // 使用 long 防止极端 delta 先发生整数溢出再绕过范围校验。
        long nextScore = (long) credit.score() + delta;
        if (nextScore < 0 || nextScore > 100) {
            throw new DataOperationException(DataOperationException.Code.CREDIT_OUT_OF_RANGE, "信用余额必须为0至100");
        }
        // 版本条件与余额范围校验共同防止覆盖；任何后续流水插入失败都会回滚余额。
        if (mapper.updateScore(userId, credit.version(), (int) nextScore) != 1) {
            throw new DataOperationException(DataOperationException.Code.VERSION_CONFLICT, "信用版本发生变化");
        }
        // 余额与流水在最外层事务一起提交；唯一索引是幂等防重的数据库兜底。
        mapper.insertLog(userId, reservationId, delta, reason, (int) nextScore);
        return mapper.findLog(reservationId, reason);
    }

    /** 限制截止时间和余额共用版本，旧版本返回零行。 */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int updateRestriction(long userId, int expectedVersion, LocalDateTime restrictedUntil) {
        ParameterCheckUtil.validateId(userId);
        if (expectedVersion < 0) throw new IllegalArgumentException("版本不能小于零");
        return mapper.updateRestriction(userId, expectedVersion, restrictedUntil);
    }
}
