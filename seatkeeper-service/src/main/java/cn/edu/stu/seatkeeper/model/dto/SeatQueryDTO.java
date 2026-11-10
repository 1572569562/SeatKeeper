package cn.edu.stu.seatkeeper.model.dto;
import jakarta.validation.constraints.Size;
/** 座位查询参数；可由未来控制器使用有效性校验后交给服务。 */
public record SeatQueryDTO(@Size(max = 64, message = "阅览室名称不能超过六十四个字符") String room, Integer floor) { }
