package cn.edu.stu.seatkeeper.service.impl;

import cn.edu.stu.seatkeeper.exception.BizErrorCode;
import cn.edu.stu.seatkeeper.exception.BusinessException;
import cn.edu.stu.seatkeeper.mapper.AccountMapper;
import cn.edu.stu.seatkeeper.model.po.CreditPO;
import cn.edu.stu.seatkeeper.model.po.UserCredentialPO;
import cn.edu.stu.seatkeeper.model.vo.LoginVO;
import cn.edu.stu.seatkeeper.security.JwtService;
import cn.edu.stu.seatkeeper.security.PasswordHasher;
import cn.edu.stu.seatkeeper.service.AuthService;
import cn.edu.stu.seatkeeper.service.CreditDataService;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 登录实现：统一返回"学号或密码错误"，避免通过错误文案枚举有效学号。
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final AccountMapper accountMapper;
    private final CreditDataService creditDataService;
    private final JwtService jwtService;

    /**
     * @param accountMapper 账号映射
     * @param creditDataService 信用读取
     * @param jwtService 令牌签发
     */
    public AuthServiceImpl(AccountMapper accountMapper, CreditDataService creditDataService,
                           JwtService jwtService) {
        this.accountMapper = accountMapper;
        this.creditDataService = creditDataService;
        this.jwtService = jwtService;
    }

    @Override
    public LoginVO login(String studentNo, String password) {
        UserCredentialPO user = accountMapper.findByStudentNo(studentNo);
        // 账号不存在、密码错误、账号停用三种情况返回同一错误，避免信息泄露。
        if (user == null || !"ACTIVE".equals(user.status())
            || !PasswordHasher.matches(password, user.passwordHash())) {
            throw new BusinessException(BizErrorCode.UNAUTHORIZED);
        }
        CreditPO credit = creditDataService.find(user.id());
        if (credit == null) {
            // 开户流程漏建信用行属于数据缺陷，记录后仍允许登录，但信用按 0 展示以引起注意。
            LOGGER.warn("用户 {} 缺少信用账户记录", user.id());
        }
        int score = credit == null ? 0 : credit.score();
        var restrictedUntil = credit == null || credit.restrictedUntil() == null
            ? null : credit.restrictedUntil().atOffset(ZoneOffset.ofHours(8));
        return new LoginVO(jwtService.issue(user.id(), user.role()), user.role(), user.name(),
            score, restrictedUntil);
    }
}
