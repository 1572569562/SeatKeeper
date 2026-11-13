package cn.edu.stu.seatkeeper.service.impl;

import cn.edu.stu.seatkeeper.exception.BizErrorCode;
import cn.edu.stu.seatkeeper.exception.BusinessException;
import cn.edu.stu.seatkeeper.mapper.CatalogMapper;
import cn.edu.stu.seatkeeper.mapper.ReservationMapper;
import cn.edu.stu.seatkeeper.model.enums.ReservationStatus;
import cn.edu.stu.seatkeeper.model.po.AdminReservationRow;
import cn.edu.stu.seatkeeper.model.po.ReservationPO;
import cn.edu.stu.seatkeeper.model.po.ReservationStatsRow;
import cn.edu.stu.seatkeeper.model.po.SeatPO;
import cn.edu.stu.seatkeeper.model.vo.AdminReservationPageVO;
import cn.edu.stu.seatkeeper.model.vo.AdminReservationVO;
import cn.edu.stu.seatkeeper.model.vo.SeatVO;
import cn.edu.stu.seatkeeper.model.vo.StatsVO;
import cn.edu.stu.seatkeeper.service.AdminService;
import cn.edu.stu.seatkeeper.service.NotificationDataService;
import cn.edu.stu.seatkeeper.service.ReservationDataService;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理端实现；所有写操作都由管理员角色在拦截器层保证。 */
@Service
public class AdminServiceImpl implements AdminService {

    private static final ZoneOffset OFFSET = ZoneOffset.ofHours(8);
    private static final DateTimeFormatter HUMAN = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm");

    private final CatalogMapper catalogMapper;
    private final ReservationMapper reservationMapper;
    private final ReservationDataService reservationDataService;
    private final NotificationDataService notificationDataService;

    /**
     * @param catalogMapper 座位读写
     * @param reservationMapper 管理端查询与状态变更
     * @param reservationDataService 预约快照读取
     * @param notificationDataService 通知写入
     */
    public AdminServiceImpl(CatalogMapper catalogMapper, ReservationMapper reservationMapper,
                            ReservationDataService reservationDataService,
                            NotificationDataService notificationDataService) {
        this.catalogMapper = catalogMapper;
        this.reservationMapper = reservationMapper;
        this.reservationDataService = reservationDataService;
        this.notificationDataService = notificationDataService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SeatVO> listSeats(String room, Integer floor, String status) {
        return catalogMapper.listSeatsForAdmin(room, floor, status).stream()
            .map(seat -> new SeatVO(seat.id(), seat.room(), seat.floor(), seat.seatNo(), seat.status()))
            .toList();
    }

    @Override
    @Transactional
    public SeatVO updateSeatStatus(long seatId, String status) {
        SeatPO seat = catalogMapper.findSeat(seatId);
        if (seat == null) {
            throw new BusinessException(BizErrorCode.NOT_FOUND);
        }
        if (!seat.status().equals(status)) {
            catalogMapper.updateSeatStatus(seatId, status);
        }
        return new SeatVO(seat.id(), seat.room(), seat.floor(), seat.seatNo(), status);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminReservationPageVO search(String studentNo, Long seatId, ReservationStatus status,
                                         OffsetDateTime from, OffsetDateTime to, int page, int size) {
        int current = Math.max(1, page);
        int pageSize = Math.min(100, Math.max(1, size));
        List<AdminReservationRow> rows = reservationMapper.searchAdmin(studentNo, seatId, status,
            toLocal(from), toLocal(to), (current - 1) * pageSize, pageSize + 1);
        boolean hasMore = rows.size() > pageSize;
        List<AdminReservationRow> pageRows = hasMore ? rows.subList(0, pageSize) : rows;
        return new AdminReservationPageVO(pageRows.stream().map(AdminServiceImpl::toVO).toList(),
            current, pageSize, hasMore);
    }

    @Override
    @Transactional
    public AdminReservationVO forceCancel(long id, String reason) {
        ReservationPO current = reservationDataService.find(id);
        if (current == null) {
            throw new BusinessException(BizErrorCode.NOT_FOUND);
        }
        if (current.status() != ReservationStatus.RESERVED
            && current.status() != ReservationStatus.CHECKED_IN) {
            throw new BusinessException(BizErrorCode.INVALID_STATE);
        }
        int rows = reservationMapper.changeStatusAsAdmin(id, current.version(), ReservationStatus.CANCELLED);
        if (rows == 0) {
            throw new BusinessException(BizErrorCode.VERSION_CONFLICT);
        }
        notificationDataService.send(current.userId(), "SYSTEM",
            "管理员已取消你的预约：" + current.slotStart().format(HUMAN) + "—"
                + current.slotEnd().format(HUMAN) + "。原因：" + reason + "。");
        AdminReservationRow row = reservationMapper.findAdminRow(id);
        if (row == null) {
            throw new BusinessException(BizErrorCode.NOT_FOUND);
        }
        return toVO(row);
    }

    @Override
    @Transactional(readOnly = true)
    public StatsVO stats(OffsetDateTime from, OffsetDateTime to) {
        ReservationStatsRow row = reservationMapper.countStats(toLocal(from), toLocal(to));
        long total = row == null ? 0 : row.total();
        long checkedIn = row == null ? 0 : row.checkedIn();
        long noShow = row == null ? 0 : row.noShow();
        double checkInRate = total == 0 ? 0 : (double) checkedIn / total;
        double noShowRate = total == 0 ? 0 : (double) noShow / total;
        return new StatsVO(total, checkedIn, noShow, round(checkInRate), round(noShowRate));
    }

    /**
     * 把带偏移的时间转换为服务端本地时间。
     * @param time 带偏移时间，可为空
     * @return 本地时间，为空时返回 null
     */
    private static LocalDateTime toLocal(OffsetDateTime time) {
        return time == null ? null : time.atZoneSameInstant(OFFSET).toLocalDateTime();
    }

    /**
     * @param value 比率
     * @return 保留四位小数
     */
    private static double round(double value) {
        return Math.round(value * 10000d) / 10000d;
    }

    /**
     * @param row 管理端行
     * @return 对外视图
     */
    private static AdminReservationVO toVO(AdminReservationRow row) {
        return new AdminReservationVO(row.id(), row.studentNo(), row.studentName(),
            row.seatId(), row.seatNo(), row.room(), row.floor(),
            row.slotStart().atOffset(OFFSET), row.slotEnd().atOffset(OFFSET),
            row.checkInDeadline().atOffset(OFFSET),
            row.checkedInAt() == null ? null : row.checkedInAt().atOffset(OFFSET),
            row.status(), row.version());
    }
}
