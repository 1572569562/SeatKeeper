package cn.edu.stu.seatkeeper.service.impl;

import cn.edu.stu.seatkeeper.mapper.ReservationMapper;
import cn.edu.stu.seatkeeper.model.enums.ReservationStatus;
import cn.edu.stu.seatkeeper.model.po.CreditPO;
import cn.edu.stu.seatkeeper.model.po.ReservationPO;
import cn.edu.stu.seatkeeper.model.vo.BatchResultVO;
import cn.edu.stu.seatkeeper.service.BatchService;
import cn.edu.stu.seatkeeper.service.CreditDataService;
import cn.edu.stu.seatkeeper.service.NotificationDataService;
import cn.edu.stu.seatkeeper.service.ReservationDataService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 批处理实现。
 *
 * <p>每条预约使用独立事务：单条失败不影响同批其他记录，守护节点下一轮会重新扫描到它。
 * 幂等由两层保证——状态条件更新（version + status）与信用流水的
 * {@code (reservation_id, reason)} 唯一键。
 */
@Service
public class BatchServiceImpl implements BatchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BatchServiceImpl.class);
    private static final ZoneOffset OFFSET = ZoneOffset.ofHours(8);
    private static final int DEFAULT_LIMIT = 100;
    private static final int NO_SHOW_PENALTY = -10;
    private static final String REASON_NO_SHOW = "NO_SHOW";
    private static final String REASON_COMPLETE = "SYSTEM_COMPLETE";
    private static final int RESTRICT_THRESHOLD = 60;
    private static final int RESTRICT_DAYS = 7;
    private static final DateTimeFormatter HUMAN = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm");

    private final ReservationDataService reservationDataService;
    private final ReservationMapper reservationMapper;
    private final CreditDataService creditDataService;
    private final NotificationDataService notificationDataService;
    private final TransactionTemplate transactionTemplate;

    /**
     * @param reservationDataService 预约持久化原语
     * @param reservationMapper 候选扫描
     * @param creditDataService 信用原语
     * @param notificationDataService 通知写入
     * @param transactionManager 事务管理器，用于逐条独立事务
     */
    public BatchServiceImpl(ReservationDataService reservationDataService,
                            ReservationMapper reservationMapper,
                            CreditDataService creditDataService,
                            NotificationDataService notificationDataService,
                            PlatformTransactionManager transactionManager) {
        this.reservationDataService = reservationDataService;
        this.reservationMapper = reservationMapper;
        this.creditDataService = creditDataService;
        this.notificationDataService = notificationDataService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public BatchResultVO expire(int limit) {
        LocalDateTime now = LocalDateTime.now(OFFSET);
        List<ReservationPO> candidates = reservationMapper.listExpiredCandidates(now, clamp(limit));
        int processed = 0;
        int skipped = 0;
        int failed = 0;
        for (ReservationPO candidate : candidates) {
            try {
                Boolean done = transactionTemplate.execute(state -> expireOne(candidate.id(), now));
                if (Boolean.TRUE.equals(done)) {
                    processed++;
                } else {
                    skipped++;
                }
            } catch (RuntimeException error) {
                failed++;
                LOGGER.warn("过期释放失败，预约 {}，异常类型：{}", candidate.id(), error.getClass().getSimpleName());
            }
        }
        return new BatchResultVO(processed, skipped, failed);
    }

    @Override
    public BatchResultVO complete(int limit) {
        LocalDateTime now = LocalDateTime.now(OFFSET);
        List<ReservationPO> candidates = reservationMapper.listCompletedCandidates(now, clamp(limit));
        int processed = 0;
        int skipped = 0;
        int failed = 0;
        for (ReservationPO candidate : candidates) {
            try {
                Boolean done = transactionTemplate.execute(state -> completeOne(candidate.id(), now));
                if (Boolean.TRUE.equals(done)) {
                    processed++;
                } else {
                    skipped++;
                }
            } catch (RuntimeException error) {
                failed++;
                LOGGER.warn("完成推进失败，预约 {}，异常类型：{}", candidate.id(), error.getClass().getSimpleName());
            }
        }
        return new BatchResultVO(processed, skipped, failed);
    }

    /**
     * 单条过期处理，必须在事务内执行。
     * @param id 预约标识
     * @param now 服务端当前时间
     * @return 是否真正推进了状态
     */
    private boolean expireOne(long id, LocalDateTime now) {
        ReservationPO current = reservationDataService.find(id);
        if (current == null || current.status() != ReservationStatus.RESERVED) {
            return false;
        }
        int rows = reservationDataService.changeStatus(id, current.userId(), current.version(),
            ReservationStatus.EXPIRED);
        if (rows == 0) {
            return false;
        }
        creditDataService.applyDelta(current.userId(), id, NO_SHOW_PENALTY, REASON_NO_SHOW);
        applyRestrictionIfNeeded(current.userId(), now);
        notificationDataService.send(current.userId(), "CREDIT",
            "预约超时未签到，座位已释放，信用 " + NO_SHOW_PENALTY + " 分。时段 "
                + current.slotStart().format(HUMAN) + "—" + current.slotEnd().format(HUMAN) + "。");
        return true;
    }

    /**
     * 单条完成处理，必须在事务内执行。
     * @param id 预约标识
     * @param now 服务端当前时间
     * @return 是否真正推进了状态
     */
    private boolean completeOne(long id, LocalDateTime now) {
        ReservationPO current = reservationDataService.find(id);
        if (current == null || current.status() != ReservationStatus.CHECKED_IN) {
            return false;
        }
        int rows = reservationDataService.changeStatus(id, current.userId(), current.version(),
            ReservationStatus.COMPLETED);
        if (rows == 0) {
            return false;
        }
        CreditPO credit = creditDataService.find(current.userId());
        // 满 100 分时跳过加分，避免 applyDelta 抛余额越界导致守护节点无限重试。
        if (credit != null && credit.score() < 100) {
            creditDataService.applyDelta(current.userId(), id, 1, REASON_COMPLETE);
        }
        notificationDataService.send(current.userId(), "CREDIT",
            "已完成本次使用，信用 +1 分。时段 "
                + current.slotStart().format(HUMAN) + "—" + current.slotEnd().format(HUMAN) + "。");
        return true;
    }

    /**
     * 扣分后低于阈值则限制预约，限制期固定 7 天。
     * @param userId 用户标识
     * @param now 服务端当前时间
     */
    private void applyRestrictionIfNeeded(long userId, LocalDateTime now) {
        CreditPO credit = creditDataService.find(userId);
        if (credit == null || credit.score() >= RESTRICT_THRESHOLD) {
            return;
        }
        creditDataService.updateRestriction(userId, credit.version(), now.plusDays(RESTRICT_DAYS));
    }

    /**
     * @param limit 请求上限
     * @return 落在 1..200 的实际批量
     */
    private static int clamp(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(200, limit);
    }
}
