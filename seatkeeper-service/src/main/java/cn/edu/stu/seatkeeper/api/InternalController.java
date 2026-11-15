package cn.edu.stu.seatkeeper.api;

import cn.edu.stu.seatkeeper.model.dto.BatchDTO;
import cn.edu.stu.seatkeeper.model.result.Result;
import cn.edu.stu.seatkeeper.model.result.ResultUtil;
import cn.edu.stu.seatkeeper.model.vo.BatchResultVO;
import cn.edu.stu.seatkeeper.model.vo.NotificationPageVO;
import cn.edu.stu.seatkeeper.service.BatchService;
import cn.edu.stu.seatkeeper.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部接口：守护节点触发批处理，通知网关增量读取。
 *
 * <p>由 {@code X-Internal-Token} 共享密钥保护，不接受用户令牌。
 */
@RestController
@RequestMapping("/api/v1/internal")
public class InternalController {

    private static final int DEFAULT_LIMIT = 100;

    private final BatchService batchService;
    private final NotificationService notificationService;

    /**
     * @param batchService 批处理用例
     * @param notificationService 通知读取用例
     */
    public InternalController(BatchService batchService, NotificationService notificationService) {
        this.batchService = batchService;
        this.notificationService = notificationService;
    }

    /**
     * @param request 批量上限，可为空
     * @return 批次结果
     */
    @PostMapping("/reservations/expire-batch")
    public Result<BatchResultVO> expireBatch(@Valid @RequestBody(required = false) BatchDTO request) {
        return ResultUtil.success(batchService.expire(limitOf(request)));
    }

    /**
     * @param request 批量上限，可为空
     * @return 批次结果
     */
    @PostMapping("/reservations/complete-batch")
    public Result<BatchResultVO> completeBatch(@Valid @RequestBody(required = false) BatchDTO request) {
        return ResultUtil.success(batchService.complete(limitOf(request)));
    }

    /**
     * @param userId 收件用户
     * @param afterId 上次游标
     * @param limit 本批上限
     * @return 增量结果
     */
    @GetMapping("/notifications")
    public Result<NotificationPageVO> notifications(@RequestParam long userId,
                                                    @RequestParam(defaultValue = "0") long afterId,
                                                    @RequestParam(defaultValue = "50") int limit) {
        return ResultUtil.success(notificationService.after(userId, afterId, limit));
    }

    /**
     * @param request 请求体，可为空
     * @return 实际批量
     */
    private static int limitOf(BatchDTO request) {
        return request == null || request.limit() == null ? DEFAULT_LIMIT : request.limit();
    }
}
