package cn.edu.stu.seatkeeper.api;
import cn.edu.stu.seatkeeper.model.vo.HealthVO;
import cn.edu.stu.seatkeeper.service.HealthService;
import cn.edu.stu.seatkeeper.exception.BusinessException;
import cn.edu.stu.seatkeeper.exception.CommonErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/** 验证视图模型、响应契约以及异常信息的公开边界。 */
@WebMvcTest(HealthController.class)
@org.springframework.context.annotation.Import(HealthControllerTest.ConversionProbeController.class)
class HealthControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean HealthService healthService;
    /** 仅用于测试框架的参数转换，不加入实际应用扫描。 */
    @org.springframework.web.bind.annotation.RestController
    static class ConversionProbeController {
        /**
         * 仅供MVC测试触发整型参数转换，不作为生产接口打包。
         * @param value 待校验值或测试输入
         */
        @org.springframework.web.bind.annotation.GetMapping("/test/number")
        String number(@org.springframework.web.bind.annotation.RequestParam int value) {
            return Integer.toString(value);
        }
    }
    /**
     * 验证整型参数收到非数字输入时返回参数错误，而不是服务器错误。
     */
    @Test void rejectsInvalidTypedRequestParameter() throws Exception {
        mvc.perform(get("/test/number").param("value", "abc")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("code").value("INVALID_ARGUMENT"));
    }
    /**
     * 验证不存在的路由仍返回未找到状态，不被兜底异常处理改成服务器错误。
     */
    @Test void preservesMissingRouteStatus() throws Exception {
        mvc.perform(get("/api/v1/missing")).andExpect(status().isNotFound());
    }
    /**
     * 验证不支持的请求方法保留正确状态和允许方法响应头。
     */
    @Test void preservesMethodNotAllowedAndAllowHeader() throws Exception {
        mvc.perform(post("/api/v1/health")).andExpect(status().isMethodNotAllowed())
            .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("GET")));
    }
    /**
     * 验证数据库连通时健康接口返回约定的成功码、字段和视图数据。
     */
    @Test void reportsReachableDatabase() throws Exception {
        when(healthService.check()).thenReturn(new HealthVO("UP", "UP"));
        mvc.perform(get("/api/v1/health")).andExpect(status().isOk())
            .andExpect(jsonPath("code").value("OK")).andExpect(jsonPath("message").value("操作成功"))
            .andExpect(jsonPath("data.status").value("UP")).andExpect(jsonPath("data.database").value("UP"));
    }
    /**
     * 验证数据库连接异常不会向调用者泄露连接串或密码等内部信息。
     */
    @Test void hidesDatabaseConnectionDetails() throws Exception {
        when(healthService.check()).thenThrow(new DataAccessResourceFailureException("jdbc:mysql://private/password=secret"));
        mvc.perform(get("/api/v1/health")).andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("code").value("DATABASE_UNAVAILABLE"))
            .andExpect(jsonPath("message").value("数据库暂不可用")).andExpect(jsonPath("data").isEmpty());
    }
    /**
     * 验证参数异常返回统一提示，不直接回显未经处理的输入。
     */
    @Test void hidesInvalidInputDetails() throws Exception {
        when(healthService.check()).thenThrow(new IllegalArgumentException("private input"));
        mvc.perform(get("/api/v1/health")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("code").value("INVALID_ARGUMENT")).andExpect(jsonPath("message").value("请求参数不合法"));
    }
    /**
     * 验证业务异常被映射为对应的稳定错误码和响应状态。
     */
    @Test void translatesBusinessErrorCode() throws Exception {
        when(healthService.check()).thenThrow(new BusinessException(CommonErrorCode.INVALID_ARGUMENT));
        mvc.perform(get("/api/v1/health")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("code").value("INVALID_ARGUMENT"));
    }
    /**
     * 验证未处理异常返回安全的服务器错误提示，不回显内部细节。
     */
    @Test void hidesUnexpectedExceptionDetails() throws Exception {
        when(healthService.check()).thenThrow(new IllegalStateException("secret internal detail"));
        mvc.perform(get("/api/v1/health")).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("code").value("INTERNAL_ERROR")).andExpect(jsonPath("message").value("服务暂时无法处理请求"));
    }
}
