package cn.edu.stu.seatkeeper.service;
import cn.edu.stu.seatkeeper.service.impl.HealthServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessResourceFailureException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
/** 验证探活结果确实依赖数据源，不能固定返回成功。 */
class HealthServiceTest {
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final HealthService service = new HealthServiceImpl(jdbcTemplate);
    /**
     * 验证健康服务只有在数据库探活查询成功时才返回可用状态。
     */
    @Test void reportsDatabaseSuccess() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        assertThat(service.check().database()).isEqualTo("UP");
    }
    /**
     * 验证数据库探活返回非预期值时拒绝报告成功。
     */
    @Test void rejectsUnexpectedProbeValue() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(0);
        assertThatThrownBy(service::check).isInstanceOf(IllegalStateException.class);
    }
    /**
     * 验证数据访问异常交由统一异常处理层转换，不伪造成功状态。
     */
    @Test void propagatesDatabaseFailureForUnifiedHandling() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenThrow(new DataAccessResourceFailureException("unavailable"));
        assertThatThrownBy(service::check).isInstanceOf(DataAccessResourceFailureException.class);
    }
}
