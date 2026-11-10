package cn.edu.stu.seatkeeper.service;
import cn.edu.stu.seatkeeper.model.po.NotificationPO;
import java.time.LocalDateTime;
import java.util.List;

/** 成员四提供的持久化服务契约；事务和数据库访问由实现类承担。 */
public interface NotificationDataService {
    /**
     * 串行化同用户通知的分配与提交，避免游标跳过未提交记录。
     * @param userId 用户标识
     * @param type 通知类型
     * @param content 通知内容
     * @return 查询或持久化结果
     */
    NotificationPO send(long userId, String type, String content);

    /**
     * 按标识倒序分页读取指定用户的通知。
     * @param userId 用户标识
     * @param offset 从零开始的偏移
     * @param limit 批量上限，范围为一至一千
     * @return 查询或持久化结果
     */
    List<NotificationPO> page(long userId, int offset, int limit);

    /**
     * 按标识正序读取游标之后的用户通知。
     * @param userId 用户标识
     * @param afterId 上次游标，首次为零
     * @param limit 批量上限，范围为一至一千
     * @return 查询或持久化结果
     */
    List<NotificationPO> after(long userId, long afterId, int limit);

    /**
     * 仅将属于指定用户的通知标记为已读。
     * @param userId 用户标识
     * @param ids 通知标识集合
     * @return 成功修改的行数
     */
    int markRead(long userId, List<Long> ids);

    /**
     * 限量清理截止时间之前的已读通知，仅供内部调度使用。
     * @param cutoff 不包含的清理截止时间
     * @param limit 批量上限，范围为一至一千
     * @return 成功修改的行数
     */
    int cleanupReadBefore(LocalDateTime cutoff, int limit);
}
