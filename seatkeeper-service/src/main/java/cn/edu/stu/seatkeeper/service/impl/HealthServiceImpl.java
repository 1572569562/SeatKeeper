package cn.edu.stu.seatkeeper.service.impl;

import cn.edu.stu.seatkeeper.service.HealthService;
import cn.edu.stu.seatkeeper.model.vo.HealthVO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
/** 执行轻量探活查询，数据库异常统一交由异常处理器转换。 */
@Service
public class HealthServiceImpl implements HealthService {
    private final JdbcTemplate jdbcTemplate;
    /** @param jdbcTemplate 受连接池管理的数据库访问组件 */
    public HealthServiceImpl(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }
    /**
     * 执行轻量数据库探活查询，结果异常或连接失败时交由统一异常处理器响应。
     */
    @Override
    public HealthVO check() {
        Integer value = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        if (!Integer.valueOf(1).equals(value)) throw new IllegalStateException("数据库探活结果异常");
        return new HealthVO("UP", "UP");
    }
}
