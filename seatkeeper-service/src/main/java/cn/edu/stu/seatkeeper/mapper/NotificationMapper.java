package cn.edu.stu.seatkeeper.mapper;

import cn.edu.stu.seatkeeper.model.po.NotificationPO;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 所有面向用户的读写条件都包含 user_id。 */
@Mapper
public interface NotificationMapper {
    /**
     * 插入指定用户的未读通知，由调用服务保证同用户的提交顺序。
     * @param userId 用户标识
     * @param type 通知类型
     * @param content 通知内容
     */
    int insert(@Param("userId") long userId, @Param("type") String type, @Param("content") String content);
    /**
     * 取得当前事务所用连接最近插入的自增标识，必须紧接同连接插入调用。
     */
    long lastInsertId();
    /**
     * 同时按通知标识和用户标识读取记录，防止跨用户读取。
     * @param id 记录标识
     * @param userId 用户标识
     */
    NotificationPO findForUser(@Param("id") long id, @Param("userId") long userId);
    /**
     * 按标识倒序分页读取指定用户的通知，偏移从零开始。
     * @param userId 用户标识
     * @param offset 从零开始的分页偏移
     * @param limit 本批或本页数量上限
     */
    List<NotificationPO> page(@Param("userId") long userId, @Param("offset") int offset, @Param("limit") int limit);
    /**
     * 按标识正序补拉严格大于游标的用户通知，首次游标可为零。
     * @param userId 用户标识
     * @param afterId 上次已取得的通知游标，首次为零
     * @param limit 本批或本页数量上限
     */
    List<NotificationPO> after(@Param("userId") long userId, @Param("afterId") long afterId, @Param("limit") int limit);
    /**
     * 只把属于该用户的未读通知标记为已读，返回实际变化行数。
     * @param userId 用户标识
     * @param ids 通知标识集合
     */
    int markRead(@Param("userId") long userId, @Param("ids") List<Long> ids);
    /**
     * 限量清理严格早于截止时间的已读通知，仅供内部调度使用。
     * @param cutoff 不包含的清理截止时间
     * @param limit 本批或本页数量上限
     */
    int cleanupReadBefore(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);
}
