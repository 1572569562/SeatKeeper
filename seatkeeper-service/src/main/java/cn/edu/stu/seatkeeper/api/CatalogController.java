package cn.edu.stu.seatkeeper.api;

import cn.edu.stu.seatkeeper.model.result.Result;
import cn.edu.stu.seatkeeper.model.result.ResultUtil;
import cn.edu.stu.seatkeeper.model.vo.SeatVO;
import cn.edu.stu.seatkeeper.model.vo.SlotVO;
import cn.edu.stu.seatkeeper.service.CatalogService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 座位与时段查询，需携带学生或管理员令牌。 */
@RestController
@RequestMapping("/api/v1")
public class CatalogController {

    private final CatalogService catalogService;

    /** @param catalogService 目录查询用例 */
    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    /**
     * @param room 阅览室，可为空
     * @param floor 楼层，可为空
     * @return 座位列表
     */
    @GetMapping("/seats")
    public Result<List<SeatVO>> seats(@RequestParam(required = false) String room,
                                      @RequestParam(required = false) Integer floor) {
        return ResultUtil.success(catalogService.listSeats(room, floor));
    }

    /**
     * @param date 日期，缺省为服务端当天
     * @return 当天固定时段
     */
    @GetMapping("/slots")
    public Result<List<SlotVO>> slots(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResultUtil.success(catalogService.listSlots(date));
    }
}
