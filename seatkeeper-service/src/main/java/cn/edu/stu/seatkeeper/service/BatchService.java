package cn.edu.stu.seatkeeper.service;

import cn.edu.stu.seatkeeper.model.vo.BatchResultVO;

/** 内部批处理用例：由守护节点按固定节奏调用。 */
public interface BatchService {

    /**
     * 过期释放：RESERVED 且已过签到截止 → EXPIRED，扣 10 分，必要时限制信用。
     * @param limit 本批上限
     * @return 批次结果
     */
    BatchResultVO expire(int limit);

    /**
     * 正常完成：CHECKED_IN 且时段已结束 → COMPLETED，加 1 分（满 100 跳过加分）。
     * @param limit 本批上限
     * @return 批次结果
     */
    BatchResultVO complete(int limit);
}
