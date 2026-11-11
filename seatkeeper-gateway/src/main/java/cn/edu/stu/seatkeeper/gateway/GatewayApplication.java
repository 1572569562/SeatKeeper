package cn.edu.stu.seatkeeper.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 通知网关启动类。
 *
 * <p>网关是独立进程，固定端口 8082，与 A 机的业务服务（8080/8081）互不干扰。
 * 它只读 notification 表，不写任何业务表。
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
