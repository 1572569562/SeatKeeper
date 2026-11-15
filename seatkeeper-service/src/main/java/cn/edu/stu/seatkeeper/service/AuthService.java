package cn.edu.stu.seatkeeper.service;

import cn.edu.stu.seatkeeper.model.vo.LoginVO;

/** 认证用例：校验凭证并签发令牌。 */
public interface AuthService {

    /**
     * 学号密码登录。
     * @param studentNo 学号
     * @param password 明文密码
     * @return 令牌与用户摘要
     * @throws cn.edu.stu.seatkeeper.exception.BusinessException 凭证错误或账号不可用
     */
    LoginVO login(String studentNo, String password);
}
