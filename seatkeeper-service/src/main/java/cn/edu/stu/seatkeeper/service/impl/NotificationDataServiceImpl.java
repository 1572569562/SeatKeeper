package cn.edu.stu.seatkeeper.service.impl;

import cn.edu.stu.seatkeeper.mapper.CreditMapper;
import cn.edu.stu.seatkeeper.mapper.NotificationMapper;
import cn.edu.stu.seatkeeper.model.po.NotificationPO;
import cn.edu.stu.seatkeeper.exception.DataOperationException;
import cn.edu.stu.seatkeeper.utils.ParameterCheckUtil;
import cn.edu.stu.seatkeeper.service.NotificationDataService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用户通知的分页、游标读取和有限批次清理。 */
@Service
@Transactional(readOnly = true)
public class NotificationDataServiceImpl implements NotificationDataService {
    private final NotificationMapper mapper;
    private final CreditMapper credits;

    /**
     * 通过构造器注入所需持久化接口，数据库操作和事务交由Spring管理。
     * @param mapper 当前业务的持久化映射接口
     * @param credits 信用锁行映射接口
     */
    public NotificationDataServiceImpl(NotificationMapper mapper, CreditMapper credits) {
        this.mapper = mapper;
        this.credits = credits;
    }

    /**
     * 先取得用户信用行锁，再分配通知标识，避免提交乱序造成游标漏读。
     * @param userId 用户标识
     * @param type 通知类型
     * @param content 通知内容
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public NotificationPO send(long userId, String type, String content) {
        ParameterCheckUtil.validateId(userId);
        ParameterCheckUtil.validateText(type, 32);
        ParameterCheckUtil.validateText(content, 1000);
        // 同用户在分配通知ID前锁信用主键行，锁随最外层事务提交释放，保证游标顺序。
        // 不升级用户外键的共享锁，且与信用变动保持先信用、后通知的锁顺序。
        if (credits.lockByUser(userId) == null) {
            throw new DataOperationException(DataOperationException.Code.NOT_FOUND, "用户信用账户不存在，不能保证通知有序提交");
        }
        // 锁尚未释放时插入并读取同一连接的自增标识；外层事务失败时通知也会回滚。
        mapper.insert(userId, type, content);
        return mapper.findForUser(mapper.lastInsertId(), userId);
    }

    /** offset 从零开始；每页按 id 倒序，适用于历史列表。 */
    @Override
    public List<NotificationPO> page(long userId, int offset, int limit) {
        ParameterCheckUtil.validateId(userId);
        ParameterCheckUtil.validatePage(offset, limit);
        return mapper.page(userId, offset, limit);
    }

    /** 返回严格大于 afterId 的数据，按 id 正序；首次可传零。 */
    @Override
    public List<NotificationPO> after(long userId, long afterId, int limit) {
        ParameterCheckUtil.validateId(userId);
        if (afterId < 0) throw new IllegalArgumentException("游标不能小于零");
        ParameterCheckUtil.validateLimit(limit);
        // 必须同时限定用户和严格大于游标，返回升序结果；通知标识不要求连续。
        return mapper.after(userId, afterId, limit);
    }

    /**
     * 只把属于该用户的未读通知标记为已读，返回实际变化行数。
     * @param userId 用户标识
     * @param ids 通知标识集合
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int markRead(long userId, List<Long> ids) {
        ParameterCheckUtil.validateId(userId);
        Objects.requireNonNull(ids, "通知列表不能为空");
        // 空集合直接结束，避免生成无效的空集合查询；映射SQL仍会再次限制用户归属。
        if (ids.isEmpty()) return 0;
        ParameterCheckUtil.validateLimit(ids.size());
        ids.forEach(id -> { Objects.requireNonNull(id, "通知标识不能为空"); ParameterCheckUtil.validateId(id); });
        return mapper.markRead(userId, ids);
    }

    /** 仅内部调度调用：不按用户限制，但只删已读且早于截止时间的数据。 */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int cleanupReadBefore(LocalDateTime cutoff, int limit) {
        Objects.requireNonNull(cutoff, "清理截止时间不能为空");
        ParameterCheckUtil.validateLimit(limit);
        return mapper.cleanupReadBefore(cutoff, limit);
    }
}
