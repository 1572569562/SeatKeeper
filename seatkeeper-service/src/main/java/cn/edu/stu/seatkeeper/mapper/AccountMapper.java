package cn.edu.stu.seatkeeper.mapper;

import cn.edu.stu.seatkeeper.model.po.UserCredentialPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 认证所需的账号读写；密码哈希只在此层出现，不进入对外模型。 */
@Mapper
public interface AccountMapper {

    /**
     * @param studentNo 学号
     * @return 含密码哈希的凭证快照，不存在时返回 null
     */
    UserCredentialPO findByStudentNo(@Param("studentNo") String studentNo);

    /**
     * @param studentNo 学号
     * @return 主键，不存在时返回 null
     */
    Long findIdByStudentNo(@Param("studentNo") String studentNo);

    /**
     * @param studentNo 学号
     * @param name 姓名
     * @param passwordHash 密码哈希
     * @param role 角色
     * @return 影响行数
     */
    int insertUser(@Param("studentNo") String studentNo, @Param("name") String name,
                   @Param("passwordHash") String passwordHash, @Param("role") String role);

    /**
     * 与建号在同一事务中执行；已存在时不覆盖。
     * @param userId 用户标识
     * @return 影响行数
     */
    int insertCreditIfAbsent(@Param("userId") long userId);
}
