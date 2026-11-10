package cn.edu.stu.seatkeeper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
/** 应用启动入口，位于根包以覆盖各层组件扫描。 */
@SpringBootApplication
public class SeatkeeperServiceApplication {
    /** @param args 启动参数 */
    public static void main(String[] args) { SpringApplication.run(SeatkeeperServiceApplication.class, args); }
}
