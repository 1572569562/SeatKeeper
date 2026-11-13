package cn.edu.stu.seatkeeper.model.vo;

/**
 * 批处理结果。
 *
 * @param processed 成功推进状态的数量
 * @param skipped 因状态已变化或版本冲突跳过的数量
 * @param failed 处理异常的数量（下一轮会被重新扫描）
 */
public record BatchResultVO(int processed, int skipped, int failed) { }
