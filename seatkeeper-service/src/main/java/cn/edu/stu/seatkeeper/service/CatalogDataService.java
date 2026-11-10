package cn.edu.stu.seatkeeper.service;

import cn.edu.stu.seatkeeper.model.dto.SeatQueryDTO;
import cn.edu.stu.seatkeeper.model.po.SeatPO;
import cn.edu.stu.seatkeeper.model.po.SlotPO;
import cn.edu.stu.seatkeeper.model.po.UserPO;
import java.time.LocalDateTime;
import java.util.List;

/** 成员四提供的持久化服务契约；事务和数据库访问由实现类承担。 */
public interface CatalogDataService {
    /**
     * 根据用户标识查询不含密码的用户快照。
     * @param id 记录标识
     * @return 查询或持久化结果
     */
    UserPO findUser(long id);

    /**
     * 按阅览室和楼层查询座位，空条件表示不限。
     * @param room 阅览室，可为空
     * @param floor 楼层，可为空
     * @return 查询或持久化结果
     */
    List<SeatPO> listSeats(String room, Integer floor);

    /**
     * 按开始时间半开区间查询固定时段。
     * @param from 包含的起始时间
     * @param until 不包含的截止时间
     * @return 查询或持久化结果
     */
    List<SlotPO> listSlots(LocalDateTime from, LocalDateTime until);

    /**
     * 使用查询参数对象读取座位。
     * @param query 阅览室与楼层条件
     * @return 匹配的座位快照
     */
    List<SeatPO> listSeats(SeatQueryDTO query);
}
