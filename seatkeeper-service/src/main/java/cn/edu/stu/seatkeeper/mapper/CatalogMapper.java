package cn.edu.stu.seatkeeper.mapper;

import cn.edu.stu.seatkeeper.model.po.SeatPO;
import cn.edu.stu.seatkeeper.model.po.SlotPO;
import cn.edu.stu.seatkeeper.model.po.UserPO;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 用户、座位和固定时段的只读映射。 */
@Mapper
public interface CatalogMapper {
    /**
     * 按用户标识读取公开资料，不查询或返回密码哈希。
     * @param id 记录标识
     */
    UserPO findUser(@Param("id") long id);
    /**
     * 按阅览室和楼层筛选座位，未指定的条件不参与过滤。
     * @param room 阅览室筛选条件，可为空
     * @param floor 楼层筛选条件，可为空
     */
    List<SeatPO> listSeats(@Param("room") String room, @Param("floor") Integer floor);
    /**
     * 按开始时间的半开区间读取固定时段，包含起点、不包含终点。
     * @param from 包含的开始时间
     * @param until 不包含的结束时间
     */
    List<SlotPO> listSlots(@Param("from") LocalDateTime from, @Param("until") LocalDateTime until);
}
