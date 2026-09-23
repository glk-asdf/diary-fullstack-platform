package com.example.diary.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 签发与解析。
 * <p>Access Token 与 Refresh Token 使用同一密钥，通过有效期和 jti 区分：
 * Refresh Token 的 jti 作为 Redis 中的键，支持主动登出与踢下线。</p>
 */
@Component
public class JwtUtil {

    private static final String CLAIM_USER_ID = "uid";
    private static final String CLAIM_USERNAME = "username";

    private final SecretKey secretKey;
    private final long accessExpireSeconds;
    private final long refreshExpireSeconds;

    public JwtUtil(@Value("${diary.jwt.secret}") String secret,
                   @Value("${diary.jwt.access-expire-seconds}") long accessExpireSeconds,
                   @Value("${diary.jwt.refresh-expire-seconds}") long refreshExpireSeconds) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("diary.jwt.secret 不足 32 字节，无法满足 HS256 要求");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessExpireSeconds = accessExpireSeconds;
        this.refreshExpireSeconds = refreshExpireSeconds;
    }

    public IssuedToken createAccessToken(Long userId, String username) {
        return buildToken(userId, username, accessExpireSeconds);
    }

    public IssuedToken createRefreshToken(Long userId, String username) {
        return buildToken(userId, username, refreshExpireSeconds);
    }

    private IssuedToken buildToken(Long userId, String username, long expireSeconds) {
        Date now = new Date();
        String jti = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_USERNAME, username)
                .id(jti)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireSeconds * 1000))
                .signWith(secretKey)
                .compact();
        return new IssuedToken(token, jti, expireSeconds);
    }

    /**
     * 已签发的令牌。同时返回 jti，避免调用方为了拿 jti 再解析一次 token。
     */
    public record IssuedToken(String token, String jti, long expiresInSeconds) {
    }

    /**
     * 解析并校验签名与有效期。
     *
     * @throws io.jsonwebtoken.JwtException 签名非法或已过期
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getUserId(Claims claims) {
        Number userId = claims.get(CLAIM_USER_ID, Number.class);
        return userId == null ? null : userId.longValue();
    }

    public String getUsername(Claims claims) {
        return claims.get(CLAIM_USERNAME, String.class);
    }

    public long getAccessExpireSeconds() {
        return accessExpireSeconds;
    }

    public long getRefreshExpireSeconds() {
        return refreshExpireSeconds;
    }
}
