package cn.edu.stu.seatkeeper.service.impl;

import cn.edu.stu.seatkeeper.model.dto.SeatQueryDTO;

import cn.edu.stu.seatkeeper.mapper.CatalogMapper;
import cn.edu.stu.seatkeeper.model.po.SeatPO;
import cn.edu.stu.seatkeeper.model.po.SlotPO;
import cn.edu.stu.seatkeeper.model.po.UserPO;
import cn.edu.stu.seatkeeper.utils.ParameterCheckUtil;
import cn.edu.stu.seatkeeper.service.CatalogDataService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 提供成员3和只读验收接口使用的基础查询。 */
@Service
@Transactional(readOnly = true)
public class CatalogDataServiceImpl implements CatalogDataService {
    private final CatalogMapper mapper;

    /**
     * 通过构造器注入所需持久化接口，数据库操作和事务交由Spring管理。
     * @param mapper 当前业务的持久化映射接口
     */
    public CatalogDataServiceImpl(CatalogMapper mapper) { this.mapper = mapper; }

    /**
     * 按用户标识读取公开资料，不查询或返回密码哈希。
     * @param id 记录标识
     */
    @Override
    public UserPO findUser(long id) {
        ParameterCheckUtil.validateId(id);
        return mapper.findUser(id);
    }

    /**
     * 按阅览室和楼层筛选座位，未指定的条件不参与过滤。
     * @param room 阅览室筛选条件，可为空
     * @param floor 楼层筛选条件，可为空
     */
    @Override
    public List<SeatPO> listSeats(String room, Integer floor) {
        return mapper.listSeats(room, floor);
    }

    /** 半开区间按开始时间检索固定时段。 */
    @Override
    public List<SlotPO> listSlots(LocalDateTime from, LocalDateTime until) {
        Objects.requireNonNull(from, "开始时间不能为空");
        Objects.requireNonNull(until, "结束时间不能为空");
        // 半开区间约定与SQL一致，防止反向或空时间范围被当成有效查询。
        if (!until.isAfter(from)) throw new IllegalArgumentException("结束时间必须晚于开始时间");
        return mapper.listSlots(from, until);
    }

    /**
     * 按阅览室和楼层筛选座位，未指定的条件不参与过滤。
     * @param query 座位筛选参数对象
     */
    @Override
    public List<SeatPO> listSeats(SeatQueryDTO query) {
        Objects.requireNonNull(query, "查询条件不能为空");
        return mapper.listSeats(query.room(), query.floor());
    }
}
