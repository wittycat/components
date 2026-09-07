package com.wittycat.components.sca.consumer.controller;

import com.wittycat.components.sca.common.JwtUtil;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by chenxun.
 * Date: 2026/7/10 23:38
 * Description:
 */
@RestController
@RequestMapping("/user")
public class UserController {
    private final JwtUtil jwtUtil;

    public UserController(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestParam("userName") String userName,
                                     @RequestParam("password") String password) {
        // 1. mysql8.4 查询用户校验密码
        // userMapper.selectByName(username)
        if (!"123456".equals(password)) {
            return Map.of("code", 500, "msg", "账号密码错误");
        }
        // 2. 存入自定义载荷
        Map<String, Object> claim = new HashMap<>();
        claim.put("role", "admin");
        claim.put("userName", userName);
        String token = jwtUtil.createToken("10001", claim);
        return Map.of("code", 200, "token", token, "msg", "登录成功");
    }
}