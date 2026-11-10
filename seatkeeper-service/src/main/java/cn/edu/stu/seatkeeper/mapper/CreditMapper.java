package cn.edu.stu.seatkeeper.mapper;

import cn.edu.stu.seatkeeper.model.po.CreditLogPO;
import cn.edu.stu.seatkeeper.model.po.CreditPO;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 信用更新必须经过事务组件，不能绕过行锁和流水。 */
@Mapper
public interface CreditMapper {
    /**
     * 按用户标识读取信用余额、限制截止时间和版本，无账户时返回空。
     * @param userId 用户标识
     */
    CreditPO find(@Param("userId") long userId);
    /**
     * 锁定用户信用主键行，锁保持到最外层事务提交或回滚。
     * @param userId 用户标识
     */
    CreditPO lockByUser(@Param("userId") long userId);
    /**
     * 按预约与原因读取最新幂等流水，需先取得该用户的信用行锁。
     * @param reservationId 预约标识
     * @param reason 区分大小写的幂等原因
     */
    CreditLogPO findLog(@Param("reservationId") long reservationId, @Param("reason") String reason);
    /**
     * 按预期版本修改信用余额，同时递增版本号，旧版本返回零行。
     * @param userId 用户标识
     * @param expectedVersion 预期版本号
     * @param score 新的信用余额
     */
    int updateScore(@Param("userId") long userId, @Param("expectedVersion") int expectedVersion, @Param("score") int score);
    /**
     * 追加本次信用变化流水，预约与原因的唯一索引负责最终防重。
     * @param userId 用户标识
     * @param reservationId 预约标识
     * @param delta 信用分变化量
     * @param reason 区分大小写的幂等原因
     * @param balanceAfter 变动后的信用余额
     */
    int insertLog(@Param("userId") long userId, @Param("reservationId") long reservationId,
            @Param("delta") int delta, @Param("reason") String reason, @Param("balanceAfter") int balanceAfter);
    /**
     * 按预期版本更新信用限制截止时间，与余额共用同一个版本号。
     * @param userId 用户标识
     * @param expectedVersion 预期版本号
     * @param restrictedUntil 限制截止时间，可为空
     */
    int updateRestriction(@Param("userId") long userId, @Param("expectedVersion") int expectedVersion,
            @Param("restrictedUntil") LocalDateTime restrictedUntil);
}
