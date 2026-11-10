package cn.edu.stu.seatkeeper.service;
import cn.edu.stu.seatkeeper.model.dto.SeatQueryDTO;
import cn.edu.stu.seatkeeper.mapper.CatalogMapper;
import cn.edu.stu.seatkeeper.service.impl.CatalogDataServiceImpl;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
/** 验证查询参数对象保持筛选条件，空对象不会意外变成全量查询。 */
class CatalogDataServiceTest {
    private final CatalogMapper mapper = mock(CatalogMapper.class);
    private final CatalogDataService service = new CatalogDataServiceImpl(mapper);
    /**
     * 验证查询参数对象中的中文阅览室和可空楼层被准确传给映射层。
     */
    @Test void preservesChineseRoomAndOptionalFloor() {
        service.listSeats(new SeatQueryDTO("图书馆北阅览室", null));
        verify(mapper).listSeats("图书馆北阅览室", null);
    }
    /**
     * 验证空查询对象在访问数据库之前被拒绝，避免意外全量查询。
     */
    @Test void rejectsMissingQueryObject() {
        assertThatThrownBy(() -> service.listSeats((SeatQueryDTO)null)).isInstanceOf(NullPointerException.class);
        verifyNoInteractions(mapper);
    }
}
