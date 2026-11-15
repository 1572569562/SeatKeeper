package cn.edu.stu.seatkeeper.security;

import cn.edu.stu.seatkeeper.exception.BizErrorCode;
import cn.edu.stu.seatkeeper.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 内部接口鉴权：守护节点与通知网关携带共享密钥 {@code X-Internal-Token}。
 *
 * <p>内部接口没有用户身份，因此不能复用 JWT；密钥通过环境变量
 * {@code INTERNAL_TOKEN} 注入，比较使用常量时间。
 */
public class InternalAuthInterceptor implements HandlerInterceptor {

    /** 请求头名称。 */
    public static final String HEADER = "X-Internal-Token";

    private final byte[] expected;

    /** @param token 共享密钥，来自配置 */
    public InternalAuthInterceptor(@Value("${app.internal.token}") String token) {
        if (token == null || token.length() < 16) {
            throw new IllegalStateException("app.internal.token 至少需要 16 个字符");
        }
        this.expected = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String provided = request.getHeader(HEADER);
        if (provided == null || !MessageDigest.isEqual(expected, provided.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(BizErrorCode.FORBIDDEN);
        }
        return true;
    }
}
