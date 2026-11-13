package cn.edu.stu.seatkeeper.api;

import cn.edu.stu.seatkeeper.model.dto.ForceCancelDTO;
import cn.edu.stu.seatkeeper.model.dto.SeatStatusDTO;
import cn.edu.stu.seatkeeper.model.enums.ReservationStatus;
import cn.edu.stu.seatkeeper.model.result.Result;
import cn.edu.stu.seatkeeper.model.result.ResultUtil;
import cn.edu.stu.seatkeeper.model.vo.AdminReservationPageVO;
import cn.edu.stu.seatkeeper.model.vo.AdminReservationVO;
import cn.edu.stu.seatkeeper.model.vo.SeatVO;
import cn.edu.stu.seatkeeper.model.vo.StatsVO;
import cn.edu.stu.seatkeeper.service.AdminService;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理端接口；ADMIN 角色由拦截器统一校验。 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminService adminService;

    /** @param adminService 管理端用例 */
    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    /**
     * @param room 阅览室，可为空
     * @param floor 楼层，可为空
     * @param status 状态，可为空
     * @return 座位列表
     */
    @GetMapping("/seats")
    public Result<List<SeatVO>> seats(@RequestParam(required = false) String room,
                                      @RequestParam(required = false) Integer floor,
                                      @RequestParam(required = false) String status) {
        return ResultUtil.success(adminService.listSeats(room, floor, status));
    }

    /**
     * @param id 座位标识
     * @param request 目标状态
     * @return 更新后的座位
     */
    @PatchMapping("/seats/{id}")
    public Result<SeatVO> updateSeat(@PathVariable long id, @Valid @RequestBody SeatStatusDTO request) {
        return ResultUtil.success(adminService.updateSeatStatus(id, request.status()));
    }

    /**
     * @param studentNo 学号，可为空
     * @param seatId 座位标识，可为空
     * @param status 状态，可为空
     * @param from 时段下界，可为空
     * @param to 时段上界，可为空
     * @param page 页码
     * @param size 每页数量
     * @return 分页结果
     */
    @GetMapping("/reservations")
    public Result<AdminReservationPageVO> reservations(
            @RequestParam(required = false) String studentNo,
            @RequestParam(required = false) Long seatId,
            @RequestParam(required = false) ReservationStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResultUtil.success(adminService.search(studentNo, seatId, status, from, to, page, size));
    }

    /**
     * @param id 预约标识
     * @param request 操作原因
     * @return 取消后的预约
     */
    @PostMapping("/reservations/{id}/force-cancel")
    public Result<AdminReservationVO> forceCancel(@PathVariable long id,
                                                  @Valid @RequestBody ForceCancelDTO request) {
        return ResultUtil.success(adminService.forceCancel(id, request.reason()));
    }

    /**
     * @param from 时段下界，可为空
     * @param to 时段上界，可为空
     * @return 统计看板
     */
    @GetMapping("/stats")
    public Result<StatsVO> stats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        return ResultUtil.success(adminService.stats(from, to));
    }
}
