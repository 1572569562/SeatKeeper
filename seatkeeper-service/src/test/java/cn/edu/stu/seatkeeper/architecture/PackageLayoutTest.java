package cn.edu.stu.seatkeeper.architecture;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** 验证约定的分层边界，防止控制器绕过服务直接访问数据库。 */
class PackageLayoutTest {
    private static final String BASE = "cn.edu.stu.seatkeeper.";

    /**
     * 验证控制器依赖服务接口，服务具有实现类，公开响应使用视图而非持久化对象。
     */
    @Test
    void separatesApiServiceAndPersistence() throws Exception {
        Class<?> controller = Class.forName(BASE + "api.HealthController");
        assertThat(controller.getDeclaredFields()).allSatisfy(field ->
            assertThat(field.getType().getPackageName()).isEqualTo(BASE + "service"));
        for (String name : List.of("HealthService", "CatalogDataService", "ReservationDataService",
                "CreditDataService", "NotificationDataService")) {
            Class<?> service = Class.forName(BASE + "service." + name);
            assertThat(service.isInterface()).isTrue();
            assertThat(service.isAssignableFrom(Class.forName(BASE + "service.impl." + name + "Impl"))).isTrue();
        }
        assertThat(controller.getMethod("health").getGenericReturnType().getTypeName())
            .contains("model.result.Result", "model.vo.HealthVO").doesNotContain("model.po");
    }
}
