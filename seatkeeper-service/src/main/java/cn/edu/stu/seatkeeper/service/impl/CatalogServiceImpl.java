package cn.edu.stu.seatkeeper.service.impl;

import cn.edu.stu.seatkeeper.model.po.SeatPO;
import cn.edu.stu.seatkeeper.model.po.SlotPO;
import cn.edu.stu.seatkeeper.model.vo.SeatVO;
import cn.edu.stu.seatkeeper.model.vo.SlotVO;
import cn.edu.stu.seatkeeper.service.CatalogDataService;
import cn.edu.stu.seatkeeper.service.CatalogService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;

/** 目录查询实现：只做持久化快照到对外视图的转换。 */
@Service
public class CatalogServiceImpl implements CatalogService {

    private static final ZoneOffset OFFSET = ZoneOffset.ofHours(8);

    private final CatalogDataService catalogDataService;

    /** @param catalogDataService 成员四提供的目录持久化组件 */
    public CatalogServiceImpl(CatalogDataService catalogDataService) {
        this.catalogDataService = catalogDataService;
    }

    @Override
    public List<SeatVO> listSeats(String room, Integer floor) {
        return catalogDataService.listSeats(room, floor).stream().map(CatalogServiceImpl::toSeat).toList();
    }

    @Override
    public List<SlotVO> listSlots(LocalDate date) {
        LocalDate day = date == null ? LocalDate.now(OFFSET) : date;
        LocalDateTime from = day.atStartOfDay();
        return catalogDataService.listSlots(from, from.plusDays(1)).stream()
            .map(CatalogServiceImpl::toSlot).toList();
    }

    /**
     * @param seat 持久化快照
     * @return 对外视图
     */
    private static SeatVO toSeat(SeatPO seat) {
        return new SeatVO(seat.id(), seat.room(), seat.floor(), seat.seatNo(), seat.status());
    }

    /**
     * @param slot 持久化快照
     * @return 带 +08:00 偏移的对外视图
     */
    private static SlotVO toSlot(SlotPO slot) {
        return new SlotVO(slot.id(), slot.startTime().atOffset(OFFSET), slot.endTime().atOffset(OFFSET));
    }
}
