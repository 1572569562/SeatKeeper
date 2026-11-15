package cn.edu.stu.seatkeeper.service;

import cn.edu.stu.seatkeeper.model.vo.NotificationPageVO;
import java.util.List;

/** 通知读取用例：游标增量、分页、标记已读。 */
public interface NotificationService {

    /**
     * @param userId 当前用户
     * @param afterId 上次游标，首次为 0
     * @param limit 本批上限
     * @return 增量结果
     */
    NotificationPageVO after(long userId, long afterId, int limit);

    /**
     * @param userId 当前用户
     * @param offset 从零开始的偏移
     * @param limit 本页上限
     * @return 倒序分页
     */
    NotificationPageVO page(long userId, int offset, int limit);

    /**
     * @param userId 当前用户
     * @param ids 通知标识
     * @return 实际由未读变为已读的条数
     */
    int markRead(long userId, List<Long> ids);
}
