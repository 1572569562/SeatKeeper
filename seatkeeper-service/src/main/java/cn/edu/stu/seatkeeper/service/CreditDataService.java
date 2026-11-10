package cn.edu.stu.seatkeeper.service;
import cn.edu.stu.seatkeeper.model.po.CreditLogPO;
import cn.edu.stu.seatkeeper.model.po.CreditPO;
import cn.edu.stu.seatkeeper.exception.DataOperationException;
import java.time.LocalDateTime;

/** 成员四提供的持久化服务契约；事务和数据库访问由实现类承担。 */
public interface CreditDataService {
    /**
     * 查询当前持久化快照，无记录时返回空。
     * @param userId 用户标识
     * @return 查询或持久化结果
     */
    CreditPO find(long userId);

    /**
     * 锁定信用账户，在同一事务内更新余额并写入幂等流水。
     * @param userId 用户标识
     * @param reservationId 预约标识
     * @param delta 信用分变化量
     * @param reason 区分大小写的幂等原因
     * @return 新增流水；相同预约、原因、用户和分值的重放返回已有流水，不重复扣分
     * @throws DataOperationException 预约或账户不存在、归属不符、重放变参、余额越界或版本冲突
     * @throws IllegalArgumentException 标识不是正整数，或原因为空、空白、超过六十四个字符
     */
    CreditLogPO applyDelta(long userId, long reservationId, int delta, String reason);

    /**
     * 按预期版本更新限制截止时间，旧版本返回零行。
     * @param userId 用户标识
     * @param expectedVersion 预期版本号
     * @param restrictedUntil 限制截止时间，可为空
     * @return 成功修改的行数
     */
    int updateRestriction(long userId, int expectedVersion, LocalDateTime restrictedUntil);
}
