package cn.edu.stu.seatkeeper.api;

import cn.edu.stu.seatkeeper.model.dto.CheckInDTO;
import cn.edu.stu.seatkeeper.model.dto.CreateReservationDTO;
import cn.edu.stu.seatkeeper.model.enums.ReservationStatus;
import cn.edu.stu.seatkeeper.model.result.Result;
import cn.edu.stu.seatkeeper.model.result.ResultUtil;
import cn.edu.stu.seatkeeper.model.vo.CheckInVO;
import cn.edu.stu.seatkeeper.model.vo.ReservationPageVO;
import cn.edu.stu.seatkeeper.model.vo.ReservationVO;
import cn.edu.stu.seatkeeper.security.CurrentUser;
import cn.edu.stu.seatkeeper.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 预约接口：创建、查询、签到、取消；用户身份来自令牌而非请求参数。 */
@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    /** @param reservationService 预约用例 */
    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    /**
     * @param user 当前用户
     * @param request 座位与时段
     * @return 201 与新建预约
     */
    @PostMapping
    public ResponseEntity<Result<ReservationVO>> create(CurrentUser user,
                                                        @Valid @RequestBody CreateReservationDTO request) {
        ReservationVO created = reservationService.create(user.userId(), request.seatId(), request.slotStart());
        return ResponseEntity.status(HttpStatus.CREATED).body(ResultUtil.success(created));
    }

    /**
     * @param user 当前用户
     * @param status 状态过滤，可为空
     * @param page 页码
     * @param size 每页数量
     * @return 分页结果
     */
    @GetMapping
    public Result<ReservationPageVO> list(CurrentUser user,
                                          @RequestParam(required = false) ReservationStatus status,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return ResultUtil.success(reservationService.listMine(user.userId(), status, page, size));
    }

    /**
     * @param user 当前用户
     * @param id 预约标识
     * @return 预约详情
     */
    @GetMapping("/{id}")
    public Result<ReservationVO> detail(CurrentUser user, @PathVariable long id) {
        return ResultUtil.success(reservationService.findMine(user.userId(), id));
    }

    /**
     * @param user 当前用户
     * @param id 预约标识
     * @param request 座位码
     * @return 签到结果
     */
    @PostMapping("/{id}/check-in")
    public Result<CheckInVO> checkIn(CurrentUser user, @PathVariable long id,
                                     @Valid @RequestBody CheckInDTO request) {
        return ResultUtil.success(reservationService.checkIn(user.userId(), id, request.seatCode()));
    }

    /**
     * @param user 当前用户
     * @param id 预约标识
     * @return 取消后的预约
     */
    @PostMapping("/{id}/cancel")
    public Result<ReservationVO> cancel(CurrentUser user, @PathVariable long id) {
        return ResultUtil.success(reservationService.cancel(user.userId(), id));
    }
}
