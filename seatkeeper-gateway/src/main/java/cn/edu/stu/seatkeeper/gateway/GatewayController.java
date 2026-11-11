package cn.edu.stu.seatkeeper.gateway;

import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 网关探针接口。
 *
 * <p>W1 只需证明“进程起来了、端口对了”，因此提供两个最轻的端点：
 * <ul>
 *   <li>{@code GET /api/v1/gateway/ping} —— 业务侧存活探针，返回服务名与端口</li>
 *   <li>{@code GET /actuator/health} —— Spring Boot 标准健康检查</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/gateway")
public class GatewayController {

    private final String serviceName;
    private final int port;

    public GatewayController(
            @Value("${spring.application.name:seatkeeper-gateway}") String serviceName,
            @Value("${server.port:8082}") int port) {
        this.serviceName = serviceName;
        this.port = port;
    }

    @GetMapping("/ping")
    public PingResponse ping() {
        return new PingResponse(serviceName, "UP", port, OffsetDateTime.now().toString());
    }

    /** 探针返回体：服务名、状态、端口、当前时间。 */
    public record PingResponse(String service, String status, int port, String time) {
    }
}
