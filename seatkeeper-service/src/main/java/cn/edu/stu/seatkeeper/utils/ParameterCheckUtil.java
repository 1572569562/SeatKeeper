package cn.edu.stu.seatkeeper.utils;

/** 组件边界的通用参数检查，不决定上层业务规则。 */
public final class ParameterCheckUtil {
    /**
     * 禁止实例化无状态工具类，只通过静态方法调用。
     */
    private ParameterCheckUtil() { }

    /**
     * 校验标识为正整数，阻止无效主键进入持久化操作。
     * @param value 待校验值或测试输入
     */
    public static void validateId(long value) {
        if (value <= 0) throw new IllegalArgumentException("标识必须为正整数");
    }

    /**
     * 校验分页偏移非负，并复用批量大小的范围检查。
     * @param offset 从零开始的分页偏移
     * @param limit 本批或本页数量上限
     */
    public static void validatePage(int offset, int limit) {
        if (offset < 0) throw new IllegalArgumentException("分页偏移不能小于零");
        validateLimit(limit);
    }

    /**
     * 限制分页或批处理规模为一至一千，避免单次操作无界增长。
     * @param limit 本批或本页数量上限
     */
    public static void validateLimit(int limit) {
        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("批量大小必须为1至1000");
    }

    /**
     * 拒绝空白或过长文本，使用Unicode码点数量检查字符长度。
     * @param value 待校验值或测试输入
     * @param maxLength 允许的最大Unicode码点数量
     */
    public static void validateText(String value, int maxLength) {
        // 按Unicode码点计数，避免把一个补充字符的两个UTF-16编码单元误算成两个字符。
        if (value == null || value.isBlank() || value.codePointCount(0, value.length()) > maxLength) {
            throw new IllegalArgumentException("文本不能为空且不能超过" + maxLength + "个字符");
        }
    }
}
