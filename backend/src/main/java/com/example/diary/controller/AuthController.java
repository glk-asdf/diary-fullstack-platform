package com.example.diary.controller;

import com.example.diary.common.result.Result;
import com.example.diary.dto.LoginDTO;
import com.example.diary.dto.RefreshTokenDTO;
import com.example.diary.dto.RegisterDTO;
import com.example.diary.service.AuthService;
import com.example.diary.vo.TokenVO;
import com.example.diary.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "认证", description = "注册 / 登录 / 刷新 / 登出")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "注册")
    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody RegisterDTO dto) {
        return Result.ok(authService.register(dto));
    }

    @Operation(summary = "登录，返回 accessToken + refreshToken")
    @PostMapping("/login")
    public Result<TokenVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.ok(authService.login(dto));
    }

    @Operation(summary = "刷新令牌，旧 refreshToken 立即失效")
    @PostMapping("/refresh")
    public Result<TokenVO> refresh(@Valid @RequestBody RefreshTokenDTO dto) {
        return Result.ok(authService.refresh(dto.refreshToken()));
    }

    @Operation(summary = "登出")
    @PostMapping("/logout")
    public Result<Void> logout(@Valid @RequestBody RefreshTokenDTO dto) {
        authService.logout(dto.refreshToken());
        return Result.ok();
    }
}
