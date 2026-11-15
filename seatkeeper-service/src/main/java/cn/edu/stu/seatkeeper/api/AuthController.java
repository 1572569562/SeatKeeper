package cn.edu.stu.seatkeeper.api;

import cn.edu.stu.seatkeeper.model.dto.LoginDTO;
import cn.edu.stu.seatkeeper.model.result.Result;
import cn.edu.stu.seatkeeper.model.result.ResultUtil;
import cn.edu.stu.seatkeeper.model.vo.LoginVO;
import cn.edu.stu.seatkeeper.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 登录接口，只做参数接收与响应封装。 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    /** @param authService 认证用例 */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * @param request 学号与密码
     * @return 令牌与用户摘要
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO request) {
        return ResultUtil.success(authService.login(request.studentNo(), request.password()));
    }
}
