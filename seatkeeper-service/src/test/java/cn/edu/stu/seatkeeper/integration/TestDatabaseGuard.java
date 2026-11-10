package cn.edu.stu.seatkeeper.integration;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

/** 集中测试库防护，普通单元测试可以在完全不连接数据库时验证拒绝路径。 */
final class TestDatabaseGuard {
    private static final Pattern NAME = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*_test$");
    private static final Set<String> SAFE_OPTIONS = Set.of("serverTimezone", "connectionTimeZone", "characterEncoding",
            "useUnicode", "useSSL", "requireSSL", "verifyServerCertificate", "allowPublicKeyRetrieval",
            "connectTimeout", "socketTimeout", "forceConnectionTimeZoneToSession");

    /**
     * 禁止实例化无状态工具类，只通过静态方法调用。
     */
    private TestDatabaseGuard() { }

    /**
     * 解析并严格验证标准MySQL测试连接串，只接受以测试后缀结尾的单库目标。
     * @param jdbcUrl 待验证的测试连接串
     */
    static String databaseName(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:mysql://")) {
            throw new IllegalArgumentException("测试仅接受标准单主机MySQL JDBC URL");
        }
        URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getRawFragment() != null
                || uri.getPort() == 0 || uri.getPort() > 65535) {
            throw new IllegalArgumentException("测试数据库URL不能包含凭据、片段、多主机或非法端口");
        }
        String path = uri.getRawPath();
        if (path == null || !path.startsWith("/") || !NAME.matcher(path.substring(1)).matches()) {
            throw new IllegalArgumentException("测试数据库名称必须以字母开始并严格以_test结尾");
        }
        validateOptions(uri.getRawQuery());
        return path.substring(1);
    }

    /**
     * 核验实际连接库名与已验证目标完全相同，避免误清理其他数据库。
     * @param expected 已验证的目标库名或预期领域错误码
     * @param actual 实际连接的数据库名
     */
    static void verifyConnectedDatabase(String expected, String actual) {
        if (expected == null || actual == null || !NAME.matcher(expected).matches()
                || !NAME.matcher(actual).matches() || !expected.equals(actual)) {
            throw new IllegalArgumentException("实际连接数据库与测试目标不一致");
        }
    }

    /**
     * 在每次删除所用的同一连接上核验库名，只清理七张业务表并保留迁移历史。
     * @param jdbc 测试库数据库访问组件
     * @param jdbcUrl 待验证的测试连接串
     */
    static void clearBusinessData(JdbcTemplate jdbc, String jdbcUrl) {
        String expected = databaseName(jdbcUrl);
        for (String table : List.of("credit_log", "notification", "credit", "reservation", "slot", "seat", "app_user")) {
            jdbc.execute((ConnectionCallback<Void>) connection -> {
                // 在将执行DELETE的同一连接上核验目录，避免连接池中不同连接造成校验错位。
                try (var statement = connection.createStatement()) {
                    try (var result = statement.executeQuery("SELECT DATABASE()")) {
                        if (!result.next()) throw new IllegalArgumentException("无法确认测试连接数据库");
                        verifyConnectedDatabase(expected, result.getString(1));
                    }
                    statement.executeUpdate("DELETE FROM " + table);
                }
                return null;
            });
        }
    }

    /**
     * 验证连接参数白名单、格式和重复项，拒绝可改变数据库目标或执行额外语句的选项。
     * @param query 座位筛选参数对象
     */
    private static void validateOptions(String query) {
        if (query == null) return;
        Set<String> seen = new HashSet<>();
        for (String option : query.split("&", -1)) {
            int separator = option.indexOf('=');
            if (separator < 1) throw new IllegalArgumentException("测试数据库URL参数格式不明确");
            String key = URLDecoder.decode(option.substring(0, separator), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(option.substring(separator + 1), StandardCharsets.UTF_8);
            if (!SAFE_OPTIONS.contains(key) || !seen.add(key) || !value.matches("[A-Za-z0-9_./:+-]+")) {
                throw new IllegalArgumentException("测试数据库URL包含未允许或重复的参数");
            }
        }
    }
}
