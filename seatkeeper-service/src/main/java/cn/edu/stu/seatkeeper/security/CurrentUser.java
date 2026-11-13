package cn.edu.stu.seatkeeper.security;

import cn.edu.stu.seatkeeper.exception.BizErrorCode;
import cn.edu.stu.seatkeeper.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 当前请求的已认证用户，由 {@link AuthInterceptor} 写入请求属性，控制器以方法参数注入。
 *
 * @param userId 用户标识
 * @param role 角色
 */
public record CurrentUser(long userId, String role) {

    /** 请求属性名。 */
    public static final String ATTRIBUTE = CurrentUser.class.getName();

    /**
     * @return 是否为管理员
     */
    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    /**
     * 从请求属性读取，缺失说明拦截器未生效，按令牌无效处理。
     * @param request 当前请求
     * @return 已认证用户
     */
    public static CurrentUser from(HttpServletRequest request) {
        Object value = request.getAttribute(ATTRIBUTE);
        if (value instanceof CurrentUser user) {
            return user;
        }
        throw new BusinessException(BizErrorCode.TOKEN_INVALID);
    }
}
