package cn.edu.stu.seatkeeper.api;

import cn.edu.stu.seatkeeper.model.result.Result;
import cn.edu.stu.seatkeeper.model.result.ResultUtil;
import cn.edu.stu.seatkeeper.model.vo.HealthVO;
import cn.edu.stu.seatkeeper.service.HealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
/** 接收健康检查请求，仅编排服务调用与响应。 */
@RestController
public class HealthController {
    private final HealthService healthService;
    /** @param healthService 健康检查服务 */
    public HealthController(HealthService healthService) { this.healthService = healthService; }
    /** @return 应用与数据源的连通状态 */
    @GetMapping("/api/v1/health")
    public Result<HealthVO> health() { return ResultUtil.success(healthService.check()); }
}
