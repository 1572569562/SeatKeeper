package cn.edu.stu.seatkeeper.api;

import cn.edu.stu.seatkeeper.model.dto.MarkReadDTO;
import cn.edu.stu.seatkeeper.model.result.Result;
import cn.edu.stu.seatkeeper.model.result.ResultUtil;
import cn.edu.stu.seatkeeper.model.vo.NotificationPageVO;
import cn.edu.stu.seatkeeper.security.CurrentUser;
import cn.edu.stu.seatkeeper.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 通知接口：游标增量与标记已读。 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    /** @param notificationService 通知用例 */
    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * @param user 当前用户
     * @param afterId 上次游标
     * @param limit 本批上限
     * @return 增量结果
     */
    @GetMapping
    public Result<NotificationPageVO> after(CurrentUser user,
                                            @RequestParam(defaultValue = "0") long afterId,
                                            @RequestParam(defaultValue = "50") int limit) {
        return ResultUtil.success(notificationService.after(user.userId(), afterId, limit));
    }

    /**
     * @param user 当前用户
     * @param request 通知标识
     * @return 实际标记条数
     */
    @PostMapping("/read")
    public Result<Integer> markRead(CurrentUser user, @Valid @RequestBody MarkReadDTO request) {
        return ResultUtil.success(notificationService.markRead(user.userId(), request.ids()));
    }
}
