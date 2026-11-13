package cn.edu.stu.seatkeeper.config;

import cn.edu.stu.seatkeeper.mapper.AccountMapper;
import cn.edu.stu.seatkeeper.security.PasswordHasher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 演示环境建可登录账号。V2 种子里的账号是 {@code LOGIN_DISABLED_DEMO_ONLY}，无法登录，
 * 因此这里单独建三个真实账号，并在同一事务中建立信用账户。
 *
 * <p>只在 {@code demo} profile 生效；已存在的账号不会覆盖密码。
 */
@Component
@Profile("demo")
public class DemoAccountInitializer implements ApplicationRunner {

    private final AccountMapper accountMapper;
    private final String password;

    /**
     * @param accountMapper 账号映射
     * @param password 演示密码，来自 DEMO_PASSWORD，默认 Demo@123456
     */
    public DemoAccountInitializer(AccountMapper accountMapper,
                                  @Value("${app.demo.password:Demo@123456}") String password) {
        this.accountMapper = accountMapper;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed("20230101", "演示学生甲", "STUDENT");
        seed("20230102", "演示学生乙", "STUDENT");
        seed("90000001", "演示管理员", "ADMIN");
    }

    /**
     * @param studentNo 学号
     * @param name 姓名
     * @param role 角色
     */
    private void seed(String studentNo, String name, String role) {
        if (accountMapper.findByStudentNo(studentNo) != null) {
            return;
        }
        accountMapper.insertUser(studentNo, name, PasswordHasher.hash(password), role);
        Long userId = accountMapper.findIdByStudentNo(studentNo);
        if (userId == null) {
            throw new IllegalStateException("演示账号创建后无法读取主键：" + studentNo);
        }
        // 信用行是通知发送的用户级锁，必须在开户的同一事务中建立。
        accountMapper.insertCreditIfAbsent(userId);
    }
}
