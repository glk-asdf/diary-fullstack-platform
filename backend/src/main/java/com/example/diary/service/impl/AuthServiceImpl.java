package com.example.diary.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.diary.common.exception.BizException;
import com.example.diary.common.result.ResultCode;
import com.example.diary.common.util.JwtUtil;
import com.example.diary.converter.UserConverter;
import com.example.diary.dto.LoginDTO;
import com.example.diary.dto.RegisterDTO;
import com.example.diary.entity.User;
import com.example.diary.mapper.UserMapper;
import com.example.diary.service.AuthService;
import com.example.diary.service.RefreshTokenStore;
import com.example.diary.vo.TokenVO;
import com.example.diary.vo.UserVO;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RefreshTokenStore refreshTokenStore;

    public AuthServiceImpl(UserMapper userMapper,
                           PasswordEncoder passwordEncoder,
                           JwtUtil jwtUtil,
                           RefreshTokenStore refreshTokenStore) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.refreshTokenStore = refreshTokenStore;
    }

    @Override
    @Transactional
    public UserVO register(RegisterDTO dto) {
        Long exists = userMapper.selectCount(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, dto.username()));
        if (exists != null && exists > 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "用户名已被占用");
        }

        User user = new User();
        user.setUsername(dto.username());
        user.setPassword(passwordEncoder.encode(dto.password()));
        user.setNickname(StringUtils.hasText(dto.nickname()) ? dto.nickname() : dto.username());
        user.setEmail(dto.email());
        userMapper.insert(user);

        log.info("新用户注册成功: id={}, username={}", user.getId(), user.getUsername());
        return UserConverter.toVO(user);
    }

    @Override
    public TokenVO login(LoginDTO dto) {
        User user = userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, dto.username()));

        // 不区分「用户不存在」与「密码错误」，避免用户名枚举
        if (user == null || !passwordEncoder.matches(dto.password(), user.getPassword())) {
            throw new BizException(ResultCode.BAD_REQUEST, "用户名或密码错误");
        }

        return issueTokens(user);
    }

    @Override
    public TokenVO refresh(String refreshToken) {
        Claims claims = parseRefreshTokenOrThrow(refreshToken);
        Long userId = jwtUtil.getUserId(claims);
        String jti = claims.getId();

        if (userId == null || jti == null || !refreshTokenStore.exists(userId, jti)) {
            throw new BizException(ResultCode.UNAUTHORIZED, "登录已失效，请重新登录");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "登录已失效，请重新登录");
        }

        // 令牌轮换：旧 Refresh Token 立即失效，防止被重复使用
        refreshTokenStore.remove(userId, jti);
        return issueTokens(user);
    }

    @Override
    public void logout(String refreshToken) {
        try {
            Claims claims = jwtUtil.parse(refreshToken);
            Long userId = jwtUtil.getUserId(claims);
            if (userId != null && claims.getId() != null) {
                refreshTokenStore.remove(userId, claims.getId());
            }
        } catch (JwtException | IllegalArgumentException e) {
            // 令牌已过期或非法时，登出视为成功，避免前端卡在登出流程
            log.debug("登出时令牌解析失败，按已登出处理: {}", e.getMessage());
        }
    }

    private Claims parseRefreshTokenOrThrow(String refreshToken) {
        try {
            return jwtUtil.parse(refreshToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BizException(ResultCode.UNAUTHORIZED, "登录已失效，请重新登录");
        }
    }

    private TokenVO issueTokens(User user) {
        JwtUtil.IssuedToken access = jwtUtil.createAccessToken(user.getId(), user.getUsername());
        JwtUtil.IssuedToken refresh = jwtUtil.createRefreshToken(user.getId(), user.getUsername());

        refreshTokenStore.save(user.getId(), refresh.jti(), user.getUsername(), jwtUtil.getRefreshExpireSeconds());

        return new TokenVO(access.token(), refresh.token(), access.expiresInSeconds(), UserConverter.toVO(user));
    }
}
