package cn.edu.stu.seatkeeper.service;

import cn.edu.stu.seatkeeper.model.vo.SeatVO;
import cn.edu.stu.seatkeeper.model.vo.SlotVO;
import java.time.LocalDate;
import java.util.List;

/** 目录查询用例：座位与固定时段。 */
public interface CatalogService {

    /**
     * @param room 阅览室，可为空
     * @param floor 楼层，可为空
     * @return 座位列表，含停用座位
     */
    List<SeatVO> listSeats(String room, Integer floor);

    /**
     * @param date 日期，为空时取服务端当天
     * @return 该日全部固定时段，按开始时间正序
     */
    List<SlotVO> listSlots(LocalDate date);
}
