package cn.edu.stu.seatkeeper.service.impl;

import cn.edu.stu.seatkeeper.exception.BizErrorCode;
import cn.edu.stu.seatkeeper.exception.BusinessException;
import cn.edu.stu.seatkeeper.mapper.CatalogMapper;
import cn.edu.stu.seatkeeper.mapper.ReservationMapper;
import cn.edu.stu.seatkeeper.model.enums.ReservationStatus;
import cn.edu.stu.seatkeeper.model.po.CreditPO;
import cn.edu.stu.seatkeeper.model.po.ReservationPO;
import cn.edu.stu.seatkeeper.model.po.SeatPO;
import cn.edu.stu.seatkeeper.model.vo.CheckInVO;
import cn.edu.stu.seatkeeper.model.vo.ReservationPageVO;
import cn.edu.stu.seatkeeper.model.vo.ReservationVO;
import cn.edu.stu.seatkeeper.service.CatalogDataService;
import cn.edu.stu.seatkeeper.service.CreditDataService;
import cn.edu.stu.seatkeeper.service.NotificationDataService;
import cn.edu.stu.seatkeeper.service.ReservationDataService;
import cn.edu.stu.seatkeeper.service.ReservationService;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 预约实现：把成员四提供的持久化原语编排成业务用例。
 *
 * <p>并发安全依赖数据库唯一索引：创建时不做"先查空闲再插入"，直接插入并让冲突冒泡为 409。
 */
@Service
public class ReservationServiceImpl implements ReservationService {

    private static final ZoneOffset OFFSET = ZoneOffset.ofHours(8);
    private static final int CHECK_IN_MINUTES = 15;
    private static final int MAX_ACTIVE = 2;
    private static final DateTimeFormatter HUMAN = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm");

    private final ReservationDataService reservationDataService;
    private final ReservationMapper reservationMapper;
    private final CatalogDataService catalogDataService;
    private final CatalogMapper catalogMapper;
    private final CreditDataService creditDataService;
    private final NotificationDataService notificationDataService;

    /**
     * @param reservationDataService 预约持久化原语
     * @param reservationMapper 预约只读查询与签到更新
     * @param catalogDataService 座位与时段查询
     * @param catalogMapper 单座位读取
     * @param creditDataService 信用读取
     * @param notificationDataService 通知写入
     */
    public ReservationServiceImpl(ReservationDataService reservationDataService,
                                  ReservationMapper reservationMapper,
                                  CatalogDataService catalogDataService,
                                  CatalogMapper catalogMapper,
                                  CreditDataService creditDataService,
                                  NotificationDataService notificationDataService) {
        this.reservationDataService = reservationDataService;
        this.reservationMapper = reservationMapper;
        this.catalogDataService = catalogDataService;
        this.catalogMapper = catalogMapper;
        this.creditDataService = creditDataService;
        this.notificationDataService = notificationDataService;
    }

    @Override
    @Transactional
    public ReservationVO create(long userId, long seatId, OffsetDateTime slotStart) {
        LocalDateTime start = slotStart.atZoneSameInstant(OFFSET).toLocalDateTime();
        LocalDateTime now = LocalDateTime.now(OFFSET);
        if (!start.isAfter(now)) {
            throw new BusinessException(BizErrorCode.SLOT_INVALID);
        }
        // 起点必须精确命中已登记的固定时段，否则任意区间重叠无法被约束拦住。
        if (catalogDataService.listSlots(start, start.plusSeconds(1)).isEmpty()) {
            throw new BusinessException(BizErrorCode.SLOT_INVALID);
        }
        SeatPO seat = catalogMapper.findSeat(seatId);
        if (seat == null) {
            throw new BusinessException(BizErrorCode.NOT_FOUND);
        }
        if (!"AVAILABLE".equals(seat.status())) {
            throw new BusinessException(BizErrorCode.SEAT_DISABLED);
        }
        CreditPO credit = creditDataService.find(userId);
        if (credit != null && credit.restrictedUntil() != null && credit.restrictedUntil().isAfter(now)) {
            throw new BusinessException(BizErrorCode.CREDIT_RESTRICTED);
        }
        if (reservationMapper.countActiveByUser(userId, now) >= MAX_ACTIVE) {
            throw new BusinessException(BizErrorCode.LIMIT_EXCEEDED);
        }
        LocalDateTime deadline = start.plusMinutes(CHECK_IN_MINUTES);
        // 直接插入：同座位同时段由唯一索引裁决，冲突会以 DataOperationException 冒泡并翻译成 409。
        ReservationPO created = reservationDataService.create(userId, seatId, start, deadline);
        notificationDataService.send(userId, "RESERVATION",
            "预约成功：" + seat.room() + " " + seat.seatNo()
                + "，" + created.slotStart().format(HUMAN) + "—" + created.slotEnd().format(HUMAN)
                + "。请在 " + created.checkInDeadline().format(HUMAN) + " 前签到。");
        return toVO(created, seat);
    }

    @Override
    @Transactional(readOnly = true)
    public ReservationPageVO listMine(long userId, ReservationStatus status, int page, int size) {
        int current = Math.max(1, page);
        int pageSize = Math.min(100, Math.max(1, size));
        // 多取一条用于判断是否还有下一页，避免额外 count 查询。
        List<ReservationPO> rows = reservationMapper.listByUserAndStatus(
            userId, status, (current - 1) * pageSize, pageSize + 1);
        boolean hasMore = rows.size() > pageSize;
        List<ReservationPO> pageRows = hasMore ? rows.subList(0, pageSize) : rows;
        Map<Long, SeatPO> seats = seatIndex();
        List<ReservationVO> items = pageRows.stream()
            .map(row -> toVO(row, seats.get(row.seatId()))).toList();
        return new ReservationPageVO(items, current, pageSize, hasMore);
    }

    @Override
    @Transactional(readOnly = true)
    public ReservationVO findMine(long userId, long id) {
        ReservationPO reservation = requireOwned(userId, id);
        return toVO(reservation, catalogMapper.findSeat(reservation.seatId()));
    }

    @Override
    @Transactional
    public CheckInVO checkIn(long userId, long id, String seatCode) {
        ReservationPO reservation = requireOwned(userId, id);
        SeatPO seat = catalogMapper.findSeat(reservation.seatId());
        if (seat == null) {
            throw new BusinessException(BizErrorCode.NOT_FOUND);
        }
        // 幂等：已签到直接返回当前状态，不再改版本也不重复发通知。
        if (reservation.status() == ReservationStatus.CHECKED_IN) {
            return new CheckInVO(reservation.status(), offset(reservation.checkedInAt()));
        }
        if (reservation.status() != ReservationStatus.RESERVED) {
            throw new BusinessException(BizErrorCode.INVALID_STATE);
        }
        LocalDateTime now = LocalDateTime.now(OFFSET);
        if (now.isBefore(reservation.slotStart()) || now.isAfter(reservation.checkInDeadline())) {
            throw new BusinessException(BizErrorCode.CHECKIN_WINDOW);
        }
        if (seatCode == null || !seat.seatNo().equalsIgnoreCase(seatCode.trim())) {
            throw new BusinessException(BizErrorCode.SEAT_CODE_MISMATCH);
        }
        int rows = reservationMapper.markCheckedIn(id, userId, reservation.version(), now);
        if (rows == 0) {
            ReservationPO latest = reservationDataService.find(id);
            if (latest != null && latest.status() == ReservationStatus.CHECKED_IN) {
                return new CheckInVO(latest.status(), offset(latest.checkedInAt()));
            }
            throw new BusinessException(BizErrorCode.VERSION_CONFLICT);
        }
        ReservationPO updated = reservationDataService.find(id);
        notificationDataService.send(userId, "CHECKIN",
            "签到成功：" + seat.room() + " " + seat.seatNo()
                + "，使用时段 " + updated.slotStart().format(HUMAN) + "—" + updated.slotEnd().format(HUMAN) + "。");
        return new CheckInVO(updated.status(), offset(updated.checkedInAt()));
    }

    @Override
    @Transactional
    public ReservationVO cancel(long userId, long id) {
        ReservationPO reservation = requireOwned(userId, id);
        SeatPO seat = catalogMapper.findSeat(reservation.seatId());
        // 幂等：已取消直接返回；其他非 RESERVED 状态拒绝。
        if (reservation.status() == ReservationStatus.CANCELLED) {
            return toVO(reservation, seat);
        }
        if (reservation.status() != ReservationStatus.RESERVED) {
            throw new BusinessException(BizErrorCode.INVALID_STATE);
        }
        int rows = reservationDataService.changeStatus(id, userId, reservation.version(), ReservationStatus.CANCELLED);
        if (rows == 0) {
            ReservationPO latest = reservationDataService.find(id);
            if (latest != null && latest.status() == ReservationStatus.CANCELLED) {
                return toVO(latest, seat);
            }
            throw new BusinessException(BizErrorCode.VERSION_CONFLICT);
        }
        ReservationPO updated = reservationDataService.find(id);
        notificationDataService.send(userId, "SYSTEM",
            "已取消预约：" + (seat == null ? "" : seat.room() + " " + seat.seatNo())
                + "，" + updated.slotStart().format(HUMAN) + "—" + updated.slotEnd().format(HUMAN) + "。");
        return toVO(updated, seat);
    }

    /**
     * 读取并核验归属；不存在或不属于当前用户都返回 404，避免探测他人预约。
     * @param userId 当前用户
     * @param id 预约标识
     * @return 预约快照
     */
    private ReservationPO requireOwned(long userId, long id) {
        ReservationPO reservation = reservationDataService.find(id);
        if (reservation == null || reservation.userId() != userId) {
            throw new BusinessException(BizErrorCode.NOT_FOUND);
        }
        return reservation;
    }

    /**
     * 把座位读成索引，避免每条预约单独查库；演示数据量下成本可忽略。
     * @return 座位标识到快照的映射
     */
    private Map<Long, SeatPO> seatIndex() {
        return catalogDataService.listSeats(null, null).stream()
            .collect(Collectors.toMap(SeatPO::id, Function.identity()));
    }

    /**
     * @param time 数据库本地时间
     * @return 带 +08:00 的对外时间，为空时返回 null
     */
    private static OffsetDateTime offset(LocalDateTime time) {
        return time == null ? null : time.atOffset(OFFSET);
    }

    /**
     * @param reservation 持久化快照
     * @param seat 座位快照，可能为空（理论上被外键阻止）
     * @return 对外视图
     */
    private static ReservationVO toVO(ReservationPO reservation, SeatPO seat) {
        String seatNo = seat == null ? "" : seat.seatNo();
        String room = seat == null ? "" : seat.room();
        int floor = seat == null ? 0 : seat.floor();
        return new ReservationVO(reservation.id(), reservation.seatId(), seatNo, room, floor,
            reservation.slotStart().atOffset(OFFSET), reservation.slotEnd().atOffset(OFFSET),
            reservation.checkInDeadline().atOffset(OFFSET), reservation.status(), reservation.version());
    }
}
