package cn.edu.stu.seatkeeper.security;

import cn.edu.stu.seatkeeper.exception.BizErrorCode;
import cn.edu.stu.seatkeeper.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 解析 Bearer 令牌并写入当前用户；管理端路径额外校验角色。
 *
 * <p>公开路径：健康检查、登录、内部接口（内部接口使用独立的共享密钥校验）。
 * 由 {@code WebConfig} 创建并注册，不作为组件自动扫描，便于 MVC 切片测试。
 */
public class AuthInterceptor implements HandlerInterceptor {

    private static final String BEARER = "Bearer ";
    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private final JwtService jwtService;

    /** @param jwtService 令牌服务 */
    public AuthInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        if (isPublic(path)) {
            return true;
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER)) {
            throw new BusinessException(BizErrorCode.TOKEN_INVALID);
        }
        JwtService.Claims claims = jwtService.verify(header.substring(BEARER.length()).trim())
            .orElseThrow(() -> new BusinessException(BizErrorCode.TOKEN_INVALID));
        request.setAttribute(CurrentUser.ATTRIBUTE, new CurrentUser(claims.userId(), claims.role()));
        if (MATCHER.match("/api/v1/admin/**", path) && !"ADMIN".equals(claims.role())) {
            throw new BusinessException(BizErrorCode.FORBIDDEN);
        }
        return true;
    }

    /**
     * @param path 请求路径
     * @return 是否免鉴权
     */
    private boolean isPublic(String path) {
        return MATCHER.match("/api/v1/health", path)
            || MATCHER.match("/actuator/**", path)
            || MATCHER.match("/api/v1/auth/**", path)
            || MATCHER.match("/api/v1/internal/**", path);
    }
}
