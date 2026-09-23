package com.example.diary.service;

import com.example.diary.common.exception.BizException;
import com.example.diary.dto.LoginDTO;
import com.example.diary.dto.RegisterDTO;
import com.example.diary.vo.TokenVO;
import com.example.diary.vo.UserVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P2 核心验收：注册 / 登录 / 刷新（含令牌轮换）/ 登出的完整链路。
 * <p>数据库操作随事务回滚；Redis 不参与事务，因此在结尾显式清理该用户的令牌。</p>
 */
@SpringBootTest
@Transactional
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private RefreshTokenStore refreshTokenStore;

    @Test
    @DisplayName("注册 -> 登录 -> 刷新轮换 -> 登出，各环节的失败分支也应正确抛业务异常")
    void authFlow() {
        String username = "tester_p2";

        // ---------- 注册 ----------
        UserVO registered = authService.register(new RegisterDTO(username, "123456", "P2 测试用户", null));
        assertThat(registered.id()).isNotNull();
        assertThat(registered.username()).isEqualTo(username);
        assertThat(registered.nickname()).isEqualTo("P2 测试用户");

        assertThatThrownBy(() -> authService.register(new RegisterDTO(username, "123456", null, null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已被占用");

        // ---------- 登录 ----------
        TokenVO token = authService.login(new LoginDTO(username, "123456"));
        assertThat(token.accessToken()).isNotBlank();
        assertThat(token.refreshToken()).isNotBlank();
        assertThat(token.expiresIn()).isEqualTo(1800L);
        assertThat(token.user().username()).isEqualTo(username);

        assertThatThrownBy(() -> authService.login(new LoginDTO(username, "wrong-password")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("用户名或密码错误");

        // ---------- 刷新（令牌轮换） ----------
        TokenVO refreshed = authService.refresh(token.refreshToken());
        assertThat(refreshed.refreshToken()).isNotEqualTo(token.refreshToken());

        // 旧 refreshToken 已失效，不能重复使用
        assertThatThrownBy(() -> authService.refresh(token.refreshToken()))
                .isInstanceOf(BizException.class);

        // 非法 refreshToken
        assertThatThrownBy(() -> authService.refresh("not-a-jwt"))
                .isInstanceOf(BizException.class);

        // ---------- 登出 ----------
        authService.logout(refreshed.refreshToken());
        assertThatThrownBy(() -> authService.refresh(refreshed.refreshToken()))
                .isInstanceOf(BizException.class);

        // 清理 Redis 中该用户的令牌（Redis 不受事务回滚影响）
        refreshTokenStore.removeAll(registered.id());
    }

    @Test
    @DisplayName("内置测试账号 tester/123456 的 BCrypt 密文可被 Spring Security 正确校验")
    void builtinTesterAccountCanLogin() {
        TokenVO token = authService.login(new LoginDTO("tester", "123456"));
        assertThat(token.accessToken()).isNotBlank();
        assertThat(token.user().username()).isEqualTo("tester");

        refreshTokenStore.removeAll(token.user().id());
    }
}
