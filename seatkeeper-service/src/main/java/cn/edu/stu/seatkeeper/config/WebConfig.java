package cn.edu.stu.seatkeeper.config;

import cn.edu.stu.seatkeeper.security.AuthInterceptor;
import cn.edu.stu.seatkeeper.security.CurrentUserArgumentResolver;
import cn.edu.stu.seatkeeper.security.InternalAuthInterceptor;
import cn.edu.stu.seatkeeper.security.JwtService;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册鉴权拦截器与当前用户参数解析器。
 *
 * <p>拦截器在这里显式创建，而不是声明为组件：MVC 切片测试不会加载 {@link JwtService}，
 * 缺少令牌服务时跳过用户鉴权，测试仍然只验证控制器与异常映射本身。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final ObjectProvider<JwtService> jwtService;
    private final String internalToken;

    /**
     * @param jwtService 令牌服务，切片测试中可能不存在
     * @param internalToken 内部接口共享密钥
     */
    public WebConfig(ObjectProvider<JwtService> jwtService,
                     @Value("${app.internal.token:}") String internalToken) {
        this.jwtService = jwtService;
        this.internalToken = internalToken;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 内部接口先校验共享密钥，用户令牌拦截器已把 /internal 视为公开路径。
        if (internalToken != null && !internalToken.isBlank()) {
            registry.addInterceptor(new InternalAuthInterceptor(internalToken))
                .addPathPatterns("/api/v1/internal/**").order(0);
        }
        jwtService.ifAvailable(jwt -> registry.addInterceptor(new AuthInterceptor(jwt))
            .addPathPatterns("/api/**").order(1));
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserArgumentResolver());
    }
}
