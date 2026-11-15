package cn.edu.stu.seatkeeper.service.impl;

import cn.edu.stu.seatkeeper.model.po.NotificationPO;
import cn.edu.stu.seatkeeper.model.vo.NotificationPageVO;
import cn.edu.stu.seatkeeper.model.vo.NotificationVO;
import cn.edu.stu.seatkeeper.service.NotificationDataService;
import cn.edu.stu.seatkeeper.service.NotificationService;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 通知读取实现；游标分页多取一条判断 hasMore，避免额外查询。 */
@Service
public class NotificationServiceImpl implements NotificationService {

    private static final ZoneOffset OFFSET = ZoneOffset.ofHours(8);

    private final NotificationDataService notificationDataService;

    /** @param notificationDataService 通知持久化组件 */
    public NotificationServiceImpl(NotificationDataService notificationDataService) {
        this.notificationDataService = notificationDataService;
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationPageVO after(long userId, long afterId, int limit) {
        int size = Math.min(100, Math.max(1, limit));
        List<NotificationPO> rows = notificationDataService.after(userId, afterId, size + 1);
        boolean hasMore = rows.size() > size;
        List<NotificationPO> page = hasMore ? rows.subList(0, size) : rows;
        long lastId = page.isEmpty() ? afterId : page.get(page.size() - 1).id();
        return new NotificationPageVO(toVOs(page), lastId, hasMore);
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationPageVO page(long userId, int offset, int limit) {
        int size = Math.min(100, Math.max(1, limit));
        List<NotificationPO> rows = notificationDataService.page(userId, Math.max(0, offset), size + 1);
        boolean hasMore = rows.size() > size;
        List<NotificationPO> page = hasMore ? rows.subList(0, size) : rows;
        long lastId = page.isEmpty() ? 0 : page.get(0).id();
        return new NotificationPageVO(toVOs(page), lastId, hasMore);
    }

    @Override
    @Transactional
    public int markRead(long userId, List<Long> ids) {
        return notificationDataService.markRead(userId, ids);
    }

    /**
     * @param rows 持久化快照
     * @return 对外视图
     */
    private static List<NotificationVO> toVOs(List<NotificationPO> rows) {
        return rows.stream().map(NotificationServiceImpl::toVO).toList();
    }

    /**
     * @param row 持久化快照
     * @return 对外视图
     */
    private static NotificationVO toVO(NotificationPO row) {
        return new NotificationVO(row.id(), row.type(), row.content(), row.readFlag(),
            row.createdAt().atOffset(OFFSET));
    }
}
