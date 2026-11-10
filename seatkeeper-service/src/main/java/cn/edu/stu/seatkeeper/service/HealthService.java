package cn.edu.stu.seatkeeper.service;

import cn.edu.stu.seatkeeper.model.vo.HealthVO;
/** 应用与数据库连通状态的服务契约。 */
public interface HealthService {
    /** @return 数据源可用时的健康状态 */
    HealthVO check();
}
