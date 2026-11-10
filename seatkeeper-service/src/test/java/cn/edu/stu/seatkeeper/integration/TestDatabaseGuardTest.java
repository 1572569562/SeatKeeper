package cn.edu.stu.seatkeeper.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 只检查字符串，拒绝路径不会创建连接，更不会尝试清空一个假的数据库。 */
class TestDatabaseGuardTest {
    /**
     * 验证标准单库MySQL连接串和严格测试库后缀能够通过保护检查。
     */
    @Test
    void acceptsStandardMysqlUrlsWithStrictTestSuffix() {
        assertThat(TestDatabaseGuard.databaseName("jdbc:mysql://127.0.0.1:33416/seatkeeper_test?serverTimezone=Asia/Shanghai")).isEqualTo("seatkeeper_test");
        assertThat(TestDatabaseGuard.databaseName("jdbc:mysql://localhost/team14_test?useSSL=false&allowPublicKeyRetrieval=true")).isEqualTo("team14_test");
    }

    /**
     * 验证备份库名或仅在中间包含测试字样的名称不能被当作测试库。
     */
    @Test
    void refusesBackupNamesAndNamesThatOnlyContainTest() {
        for (String name : List.of("seatkeeper_test_backup", "seatkeeper_testimonials", "seatkeeper", "_test", "9seatkeeper_test")) {
            assertThatThrownBy(() -> TestDatabaseGuard.databaseName("jdbc:mysql://localhost/" + name))
                    .as("拒绝非严格测试库名：%s", name).isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * 验证在建立连接前拒绝带凭据、多主机或危险参数等不安全连接串。
     */
    @Test
    void refusesUnsafeOrAmbiguousJdbcUrlsWithoutOpeningConnections() {
        for (String url : Arrays.asList(null, "", "jdbc:postgresql://localhost/seatkeeper_test",
                "jdbc:mysql://localhost/", "jdbc:mysql://localhost", "jdbc:mysql://localhost/seatkeeper%5Ftest",
                "jdbc:mysql://localhost/seatkeeper_test?databaseName=production", "jdbc:mysql://localhost/seatkeeper_test?sessionVariables=autocommit=0",
                "jdbc:mysql://localhost/seatkeeper_test#other", "jdbc:mysql://user:password@localhost/seatkeeper_test",
                "jdbc:mysql://host1,host2/seatkeeper_test", "jdbc:mysql://localhost:0/seatkeeper_test")) {
            assertThatThrownBy(() -> TestDatabaseGuard.databaseName(url)).as("拒绝不明确的数据库URL")
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * 验证实际连接库名必须与配置中已验证的测试目标逐字一致。
     */
    @Test
    void verifiesActualConnectedCatalogMatchesValidatedTargetExactly() {
        TestDatabaseGuard.verifyConnectedDatabase("seatkeeper_test", "seatkeeper_test");
        for (String actual : Arrays.asList(null, "", "other_test", "seatkeeper_test_backup", "seatkeeper_testimonials")) {
            assertThatThrownBy(() -> TestDatabaseGuard.verifyConnectedDatabase("seatkeeper_test", actual))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * 验证即使实际库名与配置相同，不满足测试命名规则的目标仍被拒绝。
     */
    @Test
    void refusesUnsafeExpectedCatalogEvenWhenConnectionMatchesIt() {
        for (String unsafe : List.of("seatkeeper_test_backup", "seatkeeper_testimonials", "_test")) {
            assertThatThrownBy(() -> TestDatabaseGuard.verifyConnectedDatabase(unsafe, unsafe))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
